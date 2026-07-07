package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.dto.pt.AvailabilitySlotDto;
import com.fitmatch.dto.pt.UpdateAvailabilityRequest;
import com.fitmatch.entity.AvailabilitySlot;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.AvailabilitySlotRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.PtAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtAvailabilityServiceImpl implements PtAvailabilityService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final PtProfileRepository ptProfileRepository;

    @Override
    @Transactional
    public List<AvailabilitySlotDto> updateForGym(String gymUsername, Long ptId, UpdateAvailabilityRequest request) {
        PtProfile pt = ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        return replaceSlots(pt, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlotDto> getForGym(String gymUsername, Long ptId) {
        ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        return availabilitySlotRepository.findByPtProfile_IdOrderByDayOfWeekAscStartTimeAsc(ptId).stream()
                .map(AvailabilitySlotDto::of).toList();
    }

    @Override
    @Transactional
    public List<AvailabilitySlotDto> updateOwn(String ptUsername, UpdateAvailabilityRequest request) {
        PtProfile pt = requireOwnPt(ptUsername);
        // UC-028: PT chỉ tự cập nhật trong khuôn khổ Gym — PT bị đình chỉ không sửa lịch.
        if (pt.getStatus() == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "A suspended PT cannot update availability");
        }
        return replaceSlots(pt, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlotDto> getOwn(String ptUsername) {
        PtProfile pt = requireOwnPt(ptUsername);
        return availabilitySlotRepository.findByPtProfile_IdOrderByDayOfWeekAscStartTimeAsc(pt.getId()).stream()
                .map(AvailabilitySlotDto::of).toList();
    }

    /** Thay toàn bộ lịch tuần; validate start<end và không chồng lấn trong cùng ngày. */
    private List<AvailabilitySlotDto> replaceSlots(PtProfile pt, UpdateAvailabilityRequest request) {
        List<AvailabilitySlotDto> slots = request.getSlots();
        for (AvailabilitySlotDto s : slots) {
            if (!s.getStartTime().isBefore(s.getEndTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "startTime must be before endTime (day " + s.getDayOfWeek() + ")");
            }
        }
        List<AvailabilitySlotDto> sorted = slots.stream()
                .sorted(Comparator.comparing(AvailabilitySlotDto::getDayOfWeek)
                        .thenComparing(AvailabilitySlotDto::getStartTime))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            AvailabilitySlotDto prev = sorted.get(i - 1);
            AvailabilitySlotDto cur = sorted.get(i);
            if (prev.getDayOfWeek().equals(cur.getDayOfWeek())
                    && cur.getStartTime().isBefore(prev.getEndTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Overlapping slots on day " + cur.getDayOfWeek());
            }
        }

        availabilitySlotRepository.deleteByPtProfile_Id(pt.getId());
        List<AvailabilitySlot> saved = availabilitySlotRepository.saveAll(slots.stream()
                .map(s -> AvailabilitySlot.builder()
                        .ptProfile(pt)
                        .dayOfWeek(s.getDayOfWeek())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .build())
                .toList());
        log.info("Availability of PT {} replaced ({} slots)", pt.getId(), saved.size());
        return saved.stream().map(AvailabilitySlotDto::of).toList();
    }

    private PtProfile requireOwnPt(String username) {
        return ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
    }
}
