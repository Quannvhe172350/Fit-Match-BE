package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.dto.pt.PtAvailabilityRequest;
import com.fitmatch.dto.pt.PtAvailabilitySaveResponse;
import com.fitmatch.dto.pt.PtAvailabilitySlotDto;
import com.fitmatch.dto.pt.PtSlotCellDto;
import com.fitmatch.entity.PtAvailability;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtAvailabilityRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.PtDailyAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtDailyAvailabilityServiceImpl implements PtDailyAvailabilityService {

    /**
     * Quyết định #8: ngưỡng KHUYẾN NGHỊ, không phải điều kiện. PT khai 15 ngày
     * vẫn lưu được, vẫn xuất hiện trong kết quả tìm kiếm, khách vẫn đặt được —
     * chỉ có một banner vàng nhắc nhở.
     */
    public static final int RECOMMENDED_DAYS = 20;

    private static final Set<SessionStatus> HOLDING =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    private final PtAvailabilityRepository availabilityRepository;
    private final PtProfileRepository ptProfileRepository;
    private final PtAssignmentRepository ptAssignmentRepository;
    private final TrainingSessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PtAvailabilitySlotDto> mySlots(String ptUsername, LocalDate from, LocalDate to) {
        PtProfile pt = requirePt(ptUsername);
        return availabilityRepository
                .findByPtProfile_IdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(pt.getId(), from, to)
                .stream().map(PtAvailabilitySlotDto::of).toList();
    }

    @Override
    @Transactional
    public PtAvailabilitySaveResponse save(String ptUsername, PtAvailabilityRequest request) {
        PtProfile pt = requirePt(ptUsername);
        LocalDate from = request.getFrom();
        LocalDate to = request.getTo();
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
        }
        List<PtAvailabilitySlotDto> slots = request.getSlots() != null ? request.getSlots() : List.of();
        for (PtAvailabilitySlotDto slot : slots) {
            if (slot.getDate().isBefore(from) || slot.getDate().isAfter(to)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Khung giờ ngày " + slot.getDate() + " nằm ngoài khoảng đang lưu");
            }
            if (!slot.getStartTime().isBefore(slot.getEndTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Giờ kết thúc phải sau giờ bắt đầu (" + slot.getDate() + ")");
            }
        }

        assertBookedSlotsPreserved(pt, from, to, slots);

        // Thay trọn khoảng: xoá rồi ghi lại. Đơn giản hơn diff từng dòng và không
        // để sót khung giờ cũ khi PT bỏ bớt.
        availabilityRepository.deleteByPtProfile_IdAndSlotDateBetween(pt.getId(), from, to);
        availabilityRepository.flush();
        List<PtAvailability> saved = availabilityRepository.saveAll(dedupe(slots).stream()
                .map(slot -> PtAvailability.builder()
                        .ptProfile(pt)
                        .slotDate(slot.getDate())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .build())
                .toList());

        long daysWithSlots = availabilityRepository.countDistinctDaysFrom(pt.getId(), LocalDate.now());
        String warning = daysWithSlots < RECOMMENDED_DAYS
                ? "Bạn mới khai " + daysWithSlots + "/" + RECOMMENDED_DAYS
                        + " ngày. Lịch đã lưu, nhưng khai thêm sẽ có nhiều khách hơn."
                : null;
        log.info("PT {} saved {} slots for [{} .. {}] ({} days declared)",
                ptUsername, saved.size(), from, to, daysWithSlots);

        return PtAvailabilitySaveResponse.builder()
                .saved(saved.size())
                .daysWithSlots(daysWithSlots)
                .threshold(RECOMMENDED_DAYS)
                .warning(warning)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime) {
        List<Long> ptIds = activePtIdsOfBranch(branchId);
        if (ptIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PtProfile> profiles = profilesById(ptIds);
        Set<String> taken = takenKeys(ptIds, date, date);

        return availabilityRepository.findByPtProfile_IdInAndSlotDateAndStartTime(ptIds, date, startTime)
                .stream()
                .map(a -> cell(a, profiles, taken))
                // Chọn giờ trước thì chỉ hiện PT thật sự còn trống — ô đã kín ở
                // chiều này là nhiễu, khác với lưới (nơi ô mờ vẫn có ý nghĩa).
                .filter(c -> !c.isTaken())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to) {
        List<Long> ptIds = activePtIdsOfBranch(branchId);
        if (ptId != null) {
            if (!ptIds.contains(ptId)) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "PT không phụ trách chi nhánh này hoặc đang ngừng nhận lịch");
            }
            ptIds = List.of(ptId);
        }
        if (ptIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PtProfile> profiles = profilesById(ptIds);
        Set<String> taken = takenKeys(ptIds, from, to);

        return availabilityRepository
                .findByPtProfile_IdInAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(ptIds, from, to)
                .stream().map(a -> cell(a, profiles, taken)).toList();
    }

    // ---------- helpers ----------

    /**
     * P1-15 giữ nguyên tinh thần: không cho PT rút một khung giờ mà khách đã đặt.
     * Ở mô hình cũ đây là {@code assertUpcomingBookingsCoveredBySlots}; giờ so
     * khớp theo (ngày, giờ bắt đầu) vì lịch đã là ngày cụ thể.
     */
    private void assertBookedSlotsPreserved(PtProfile pt, LocalDate from, LocalDate to,
                                            List<PtAvailabilitySlotDto> slots) {
        Set<String> kept = slots.stream()
                .map(s -> key(s.getDate(), s.getStartTime()))
                .collect(Collectors.toSet());

        List<String> conflicts = sessionRepository
                .findByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
                        pt.getId(), SessionStatus.SCHEDULED, LocalDate.now())
                .stream()
                .filter(s -> !s.getSessionDate().isBefore(from) && !s.getSessionDate().isAfter(to))
                .filter(s -> s.getPtSlotStart() != null)
                .filter(s -> !kept.contains(key(s.getSessionDate(), s.getPtSlotStart())))
                .map(s -> s.getSessionDate() + " " + s.getPtSlotStart())
                .toList();

        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể bỏ khung giờ đã có khách đặt: " + String.join(", ", conflicts));
        }
    }

    /** Unique key của bảng là (pt, ngày, giờ bắt đầu) — bỏ trùng trước khi ghi. */
    private List<PtAvailabilitySlotDto> dedupe(List<PtAvailabilitySlotDto> slots) {
        Set<String> seen = new HashSet<>();
        List<PtAvailabilitySlotDto> unique = new ArrayList<>();
        for (PtAvailabilitySlotDto slot : slots) {
            if (seen.add(key(slot.getDate(), slot.getStartTime()))) {
                unique.add(slot);
            }
        }
        return unique;
    }

    /** PT của chi nhánh đang ACTIVE — PT tạm tắt/đình chỉ không xuất hiện trong lưới. */
    private List<Long> activePtIdsOfBranch(Long branchId) {
        List<Long> assigned = ptAssignmentRepository.findPtIdsByBranchId(branchId).stream()
                .distinct().toList();
        if (assigned.isEmpty()) {
            return List.of();
        }
        return ptProfileRepository.findAllById(assigned).stream()
                .filter(p -> p.getStatus() == PtStatus.ACTIVE)
                .map(PtProfile::getId)
                .toList();
    }

    private Map<Long, PtProfile> profilesById(List<Long> ptIds) {
        return ptProfileRepository.findAllById(ptIds).stream()
                .collect(Collectors.toMap(PtProfile::getId, Function.identity()));
    }

    /** Các ô (pt, ngày, giờ) đã bị buổi tập chiếm — một truy vấn cho cả lưới. */
    private Set<String> takenKeys(List<Long> ptIds, LocalDate from, LocalDate to) {
        Set<String> keys = new HashSet<>();
        for (TrainingSession s : sessionRepository
                .findByPtProfile_IdInAndSessionDateBetweenAndStatusIn(ptIds, from, to, HOLDING)) {
            if (s.getPtSlotStart() != null && s.getPtProfile() != null) {
                keys.add(s.getPtProfile().getId() + "|" + key(s.getSessionDate(), s.getPtSlotStart()));
            }
        }
        return keys;
    }

    private PtSlotCellDto cell(PtAvailability a, Map<Long, PtProfile> profiles, Set<String> taken) {
        PtProfile pt = profiles.get(a.getPtProfile().getId());
        return PtSlotCellDto.builder()
                .ptProfileId(a.getPtProfile().getId())
                .ptName(pt != null ? pt.getDisplayName() : null)
                .ptAvgRating(pt != null ? pt.getAvgRating() : null)
                .date(a.getSlotDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .taken(taken.contains(a.getPtProfile().getId() + "|"
                        + key(a.getSlotDate(), a.getStartTime())))
                .build();
    }

    private static String key(LocalDate date, LocalTime start) {
        return date + "T" + start;
    }

    private PtProfile requirePt(String username) {
        return ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
    }
}
