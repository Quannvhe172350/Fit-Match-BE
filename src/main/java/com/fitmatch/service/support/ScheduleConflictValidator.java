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
            reasons.add("PT hiện không hoạt động (trạng thái: " + pt.getStatus() + ")");
        }

        int day = start.getDayOfWeek().getValue();
        List<AvailabilitySlot> slots = availabilitySlotRepository
                .findByPtProfile_IdAndDayOfWeek(ptId, day);
        boolean insideAvailability = slots.stream().anyMatch(s ->
                !start.toLocalTime().isBefore(s.getStartTime())
                        && !end.toLocalTime().isAfter(s.getEndTime()));
        if (!insideAvailability) {
            reasons.add("Khung giờ nằm ngoài lịch rảnh hàng tuần của PT");
        }

        if (!blockedTimeRepository
                .findByPtProfile_IdAndStartAtLessThanAndEndAtGreaterThan(ptId, end, start).isEmpty()) {
            reasons.add("PT bận trong khoảng thời gian này");
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
            reasons.add("Chi nhánh đang ngừng hoạt động");
        }

        int day = start.getDayOfWeek().getValue();
        List<OperatingHour> hours = operatingHourRepository
                .findByGymBranch_IdOrderByDayOfWeek(branchId).stream()
                .filter(h -> h.getDayOfWeek() == day)
                .toList();
        if (hours.isEmpty()) {
            reasons.add("Chi nhánh chưa cấu hình giờ hoạt động cho ngày này — vui lòng chọn ngày khác hoặc liên hệ phòng gym");
        } else {
            // BE-17 (audit 2026-07-17): trước đây chỉ xét hours.get(0) — sai nếu một ngày
            // có nhiều khung mở cửa (vd sáng + chiều). Booking hợp lệ khi nằm TRỌN trong
            // BẤT KỲ khung đang mở nào.
            LocalTime startTime = start.toLocalTime();
            LocalTime endTime = end.toLocalTime();
            boolean withinAnyOpenWindow = hours.stream()
                    .filter(h -> !h.isClosed())
                    .anyMatch(h -> !startTime.isBefore(h.getOpenTime()) && !endTime.isAfter(h.getCloseTime()));
            if (!withinAnyOpenWindow) {
                boolean allClosed = hours.stream().allMatch(OperatingHour::isClosed);
                reasons.add(allClosed
                        ? "Chi nhánh đóng cửa vào ngày này"
                        : "Khung giờ nằm ngoài giờ mở cửa của chi nhánh");
            }
        }

        if (!blockedTimeRepository
                .findByGymBranch_IdAndStartAtLessThanAndEndAtGreaterThan(branchId, end, start).isEmpty()) {
            reasons.add("Chi nhánh có lịch chặn (bảo trì/nghỉ lễ) trong khoảng thời gian này");
        }
        return reasons;
    }

    private List<String> checkRange(LocalDateTime start, LocalDateTime end) {
        List<String> reasons = new ArrayList<>();
        if (!start.isBefore(end)) {
            reasons.add("Giờ bắt đầu phải trước giờ kết thúc");
        } else if (!start.toLocalDate().equals(end.toLocalDate())) {
            reasons.add("Lịch đặt phải bắt đầu và kết thúc trong cùng một ngày");
        }
        return reasons;
    }
}
