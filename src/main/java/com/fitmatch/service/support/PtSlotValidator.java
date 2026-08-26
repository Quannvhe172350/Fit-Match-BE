package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtLeaveRequestRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra một khung giờ PT có đặt được không.
 *
 * <p>V85 đảo chủ thể sở hữu lịch: trước đây điều kiện then chốt là "PT đã tự
 * khai khung giờ này" ({@code pt_availabilities}); giờ là "GYM đã xếp PT vào
 * một ca phủ khung giờ này, và PT không có đơn nghỉ đã duyệt phủ lên".
 *
 * <p>Năm điều kiện, đúng thứ tự rẻ tiền trước:
 * <ol>
 *   <li>PT đang ACTIVE;</li>
 *   <li>PT được phân công vào đúng chi nhánh của vé (câu 24);</li>
 *   <li>PT có CA tại chi nhánh đó, đúng ngày đó, và khung giờ khách chọn nằm
 *       đúng lưới slot của ca;</li>
 *   <li>không có đơn nghỉ APPROVED nào phủ lên khung giờ đó;</li>
 *   <li>khung giờ chưa bị buổi tập nào khác chiếm.</li>
 * </ol>
 * Sức chứa chi nhánh vẫn không tham gia (câu 27/28); giờ mở cửa tham gia GIÁN
 * TIẾP — ca đã bị ràng buộc phải nằm trong {@code operating_hours} lúc tạo.
 */
@Component
@RequiredArgsConstructor
public class PtSlotValidator {

    /** Buổi đang giữ chỗ của PT — buổi đã huỷ thì trả lại khung giờ. */
    public static final Set<SessionStatus> HOLDING_STATUSES =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    /**
     * Khung giờ đã phân giải từ ca. Thay {@code PtAvailability} ở vai trò "giá
     * trị trả về cho caller chép endTime": caller chỉ cần hai mốc giờ, không
     * cần một entity đã bị gỡ khỏi mô hình.
     */
    public record ResolvedSlot(GymShift shift, LocalTime startTime, LocalTime endTime) {
    }

    private final PtProfileRepository ptProfileRepository;
    private final PtAssignmentRepository ptAssignmentRepository;
    private final PtShiftAssignmentRepository shiftAssignmentRepository;
    private final PtLeaveRequestRepository leaveRequestRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ShiftSlotResolver slotResolver;

    /**
     * Ném 409 kèm mọi vi phạm nếu không đặt được; trả về khung giờ đã phân giải
     * để caller chép {@code endTime} vào buổi tập (giờ kết thúc do CA quyết,
     * khách chỉ chọn giờ bắt đầu).
     *
     * @param excludeSessionId buổi đang được sửa — loại trừ chính nó khi đổi khung
     *                         giờ trong cùng ngày; null khi đặt mới
     */
    public ResolvedSlot resolveSlot(Long ptId, Long branchId, LocalDate date,
                                    LocalTime slotStart, Long excludeSessionId) {
        return resolveSlot(ptId, branchId, date, slotStart, excludeSessionId, null);
    }

    /**
     * V93: {@code requiredMinutes} = độ dài buổi ghi trên VÉ. Null = không ràng
     * buộc (hành vi cũ, độ dài do ca quyết).
     *
     * <p>Đòi khớp ĐÚNG chứ không phải "ca dài hơn là được": ca 120 phút mà vé chỉ
     * mua 60 phút thì khách chiếm trọn khung của PT trong hai tiếng, gym mất một
     * suất bán. Gym muốn bán buổi 90 phút thì khai ca 90 phút — độ dài slot vốn
     * đã là thứ gym tự đặt ở {@code gym_shifts.slot_minutes}.
     */
    public ResolvedSlot resolveSlot(Long ptId, Long branchId, LocalDate date,
                                    LocalTime slotStart, Long excludeSessionId,
                                    Integer requiredMinutes) {
        List<String> reasons = new ArrayList<>();

        PtProfile pt = ptProfileRepository.findById(ptId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "PT not found: " + ptId));
        if (pt.getStatus() != PtStatus.ACTIVE) {
            reasons.add("PT hiện không nhận lịch (trạng thái: " + pt.getStatus() + ")");
        }
        if (!ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(ptId, branchId)) {
            reasons.add("PT không phụ trách chi nhánh này");
        }

        ResolvedSlot slot = findSlot(ptId, branchId, date, slotStart);
        if (slot == null) {
            reasons.add("PT không có ca làm việc phủ khung giờ " + slotStart + " ngày " + date);
        } else {
            if (isOnApprovedLeave(ptId, date, slot)) {
                reasons.add("PT đã được duyệt nghỉ vào khung giờ này");
            }
            if (isTaken(ptId, date, slotStart, excludeSessionId)) {
                reasons.add("Khung giờ này của PT đã có người đặt");
            }
            if (requiredMinutes != null) {
                long slotMinutes = java.time.Duration
                        .between(slot.startTime(), slot.endTime()).toMinutes();
                if (slotMinutes != requiredMinutes) {
                    reasons.add("Vé này quy định mỗi buổi " + requiredMinutes
                            + " phút, khung giờ vừa chọn dài " + slotMinutes + " phút");
                }
            }
        }

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể chọn PT: " + String.join("; ", reasons));
        }
        return slot;
    }

    /**
     * Ca của PT tại chi nhánh đó phủ đúng {@code slotStart} — hoặc null. Một PT
     * có thể có nhiều ca trong ngày (sáng + tối), nên phải duyệt hết.
     */
    public ResolvedSlot findSlot(Long ptId, Long branchId, LocalDate date, LocalTime slotStart) {
        for (PtShiftAssignment assignment : shiftAssignmentRepository.findActiveByPtAndDate(ptId, date)) {
            GymShift shift = assignment.getGymShift();
            if (!shift.isActive() || !shift.getGymBranch().getId().equals(branchId)) {
                continue;
            }
            LocalTime end = slotResolver.slotEndOrNull(shift, slotStart);
            if (end != null) {
                return new ResolvedSlot(shift, slotStart, end);
            }
        }
        return null;
    }

    /** Có đơn nghỉ ĐÃ DUYỆT nào phủ khung giờ này không. */
    public boolean isOnApprovedLeave(Long ptId, LocalDate date, ResolvedSlot slot) {
        List<PtLeaveRequest> leaves = leaveRequestRepository.findOverlapping(
                ptId, Set.of(LeaveStatus.APPROVED), date, date);
        return slotResolver.anyLeaveCovers(leaves, date,
                slot.startTime(), slot.endTime(), slot.shift());
    }

    /** Dùng cho lưới ngày x giờ ở FE: ô đã có người đặt thì hiển thị mờ. */
    public boolean isTaken(Long ptId, LocalDate date, LocalTime slotStart, Long excludeSessionId) {
        return trainingSessionRepository
                .findByPtProfile_IdAndSessionDateAndStatusIn(ptId, date, HOLDING_STATUSES).stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .anyMatch(s -> slotStart.equals(s.getPtSlotStart()));
    }
}
