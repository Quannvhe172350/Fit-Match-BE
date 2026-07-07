package com.fitmatch.service.support;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.entity.AvailabilitySlot;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.AvailabilitySlotRepository;
import com.fitmatch.repository.BlockedTimeRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.PtProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-030: kiểm tra xung đột lịch trước khi xác nhận booking/reschedule.
 * Trả về danh sách lý do vi phạm (rỗng = hợp lệ). Phase booking (UC-031..)
 * sẽ bổ sung kiểm tra trùng booking hiện có và capacity theo số booking.
 */
@Component
@RequiredArgsConstructor
public class ScheduleConflictValidator {

    private final PtProfileRepository ptProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final BlockedTimeRepository blockedTimeRepository;

    /** Kiểm tra PT có nhận được lịch trong [start, end) không. */
    public List<String> checkPt(Long ptId, LocalDateTime start, LocalDateTime end) {
        PtProfile pt = ptProfileRepository.findById(ptId)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        List<String> reasons = new ArrayList<>(checkRange(start, end));
        if (!reasons.isEmpty()) {
            return reasons;
        }

        if (pt.getStatus() != PtStatus.ACTIVE) {
            reasons.add("PT is not active (current status: " + pt.getStatus() + ")");
        }

        int day = start.getDayOfWeek().getValue();
        List<AvailabilitySlot> slots = availabilitySlotRepository
                .findByPtProfile_IdAndDayOfWeek(ptId, day);
        boolean insideAvailability = slots.stream().anyMatch(s ->
                !start.toLocalTime().isBefore(s.getStartTime())
                        && !end.toLocalTime().isAfter(s.getEndTime()));
        if (!insideAvailability) {
            reasons.add("Requested time is outside PT weekly availability");
        }

        if (!blockedTimeRepository
                .findByPtProfile_IdAndStartAtLessThanAndEndAtGreaterThan(ptId, end, start).isEmpty()) {
            reasons.add("PT has blocked/busy time in this period");
        }
        return reasons;
    }

    /** Kiểm tra chi nhánh có mở cửa và không bị chặn trong [start, end) không. */
    public List<String> checkBranch(Long branchId, LocalDateTime start, LocalDateTime end) {
        GymBranch branch = gymBranchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
        List<String> reasons = new ArrayList<>(checkRange(start, end));
        if (!reasons.isEmpty()) {
            return reasons;
        }

        if (!branch.isActive()) {
            reasons.add("Branch is not active");
        }

        int day = start.getDayOfWeek().getValue();
        List<OperatingHour> hours = operatingHourRepository
                .findByGymBranch_IdOrderByDayOfWeek(branchId).stream()
                .filter(h -> h.getDayOfWeek() == day)
                .toList();
        if (hours.isEmpty()) {
            reasons.add("Branch has no operating hours configured for this day");
        } else {
            OperatingHour hour = hours.get(0);
            LocalTime startTime = start.toLocalTime();
            LocalTime endTime = end.toLocalTime();
            if (hour.isClosed()) {
                reasons.add("Branch is closed on this day");
            } else if (startTime.isBefore(hour.getOpenTime()) || endTime.isAfter(hour.getCloseTime())) {
                reasons.add("Requested time is outside branch operating hours");
            }
        }

        if (!blockedTimeRepository
                .findByGymBranch_IdAndStartAtLessThanAndEndAtGreaterThan(branchId, end, start).isEmpty()) {
            reasons.add("Branch has blocked time (maintenance/holiday) in this period");
        }
        return reasons;
    }

    private List<String> checkRange(LocalDateTime start, LocalDateTime end) {
        List<String> reasons = new ArrayList<>();
        if (!start.isBefore(end)) {
            reasons.add("startAt must be before endAt");
        } else if (!start.toLocalDate().equals(end.toLocalDate())) {
            reasons.add("The requested slot must start and end on the same day");
        }
        return reasons;
    }
}
