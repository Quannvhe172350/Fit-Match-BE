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
 *   <li>PT có CA tại chi nhánh đó, đúng ngày đó, phủ ĐỦ thời lượng buổi tính từ
 *       giờ khách chọn — chuỗi slot được phép vắt qua nhiều ca liền nhau;</li>
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
    public record ResolvedSlot(GymShift shift, LocalTime startTime, LocalTime endTime,
                               List<ShiftSlotResolver.Slot> slots) {

        /** Buổi có vắt qua nhiều ca không — dùng cho log và ghi chú lịch sử. */
        public boolean spansMultipleShifts() {
            return slots.stream().map(sl -> sl.shift().getId()).distinct().count() > 1;
        }
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

        ResolvedSlot slot = findSlot(ptId, branchId, date, slotStart, requiredMinutes);
        if (slot == null) {
            reasons.add(requiredMinutes == null
                    ? "PT không có ca làm việc phủ khung giờ " + slotStart + " ngày " + date
                    : "PT không có ca làm việc phủ đủ " + requiredMinutes + " phút liền mạch từ "
                            + slotStart + " ngày " + date);
        } else {
            // Nghỉ phải kiểm trên MỌI slot của chuỗi: buổi hai tiếng vắt qua hai
            // ca mà PT chỉ nghỉ ca sau thì tiếng đầu vẫn trống — kiểm mỗi slot
            // đầu sẽ cho đặt trọn hai tiếng vào khoảng PT không có mặt.
            if (isOnApprovedLeave(ptId, date, slot)) {
                reasons.add("PT đã được duyệt nghỉ vào khung giờ này");
            }
            if (overlapsExistingSession(ptId, date, slot, excludeSessionId)) {
                reasons.add("Khung giờ này của PT đã có người đặt");
            }
        }

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể chọn PT: " + String.join("; ", reasons));
        }
        return slot;
    }

    /**
     * Chuỗi slot của PT tại chi nhánh đó, bắt đầu đúng {@code slotStart} và đủ
     * {@code requiredMinutes} — hoặc null. Chuỗi được phép vắt qua nhiều ca liền
     * nhau; xem {@link ShiftSlotResolver#chainFrom}.
     *
     * <p>Lọc ca theo CHI NHÁNH trước khi ghép: PT có thể làm ở hai cơ sở trong
     * cùng một ngày, và vé chỉ dùng được ở đúng chi nhánh của nó — ghép lẫn ca
     * chi nhánh khác vào là bán một buổi khách không thể tới tập.
     */
    public ResolvedSlot findSlot(Long ptId, Long branchId, LocalDate date,
                                 LocalTime slotStart, Integer requiredMinutes) {
        List<GymShift> shifts = shiftsOfDay(ptId, branchId, date);
        List<ShiftSlotResolver.Slot> chain =
                slotResolver.chainFrom(shifts, slotStart, requiredMinutes);
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        return new ResolvedSlot(chain.get(0).shift(), slotStart,
                chain.get(chain.size() - 1).end(), chain);
    }

    /** Ca đang hoạt động của PT tại chi nhánh đó trong ngày. */
    public List<GymShift> shiftsOfDay(Long ptId, Long branchId, LocalDate date) {
        List<GymShift> shifts = new ArrayList<>();
        for (PtShiftAssignment assignment : shiftAssignmentRepository.findActiveByPtAndDate(ptId, date)) {
            GymShift shift = assignment.getGymShift();
            if (shift.isActive() && shift.getGymBranch().getId().equals(branchId)) {
                shifts.add(shift);
            }
        }
        return shifts;
    }

    /**
     * Có đơn nghỉ ĐÃ DUYỆT nào phủ chuỗi này không — kiểm TỪNG slot, vì đơn nghỉ
     * theo phạm vi CA chỉ phủ đúng ca của nó, mà chuỗi thì có thể nằm ở hai ca.
     */
    public boolean isOnApprovedLeave(Long ptId, LocalDate date, ResolvedSlot slot) {
        List<PtLeaveRequest> leaves = leaveRequestRepository.findOverlapping(
                ptId, Set.of(LeaveStatus.APPROVED), date, date);
        return slot.slots().stream().anyMatch(part ->
                slotResolver.anyLeaveCovers(leaves, date, part.start(), part.end(), part.shift()));
    }

    /**
     * Chuỗi này có đè lên buổi nào đang giữ chỗ của PT không.
     *
     * <p>So GIAO KHOẢNG chứ không so bằng giờ bắt đầu như trước: từ khi buổi có
     * thể dài hơn một slot, hai buổi đè nhau mà lệch giờ bắt đầu là chuyện có
     * thật — buổi 08:00–10:00 và buổi 09:00–10:00 không trùng mốc bắt đầu nhưng
     * PT thì không thể dạy cả hai.
     */
    public boolean overlapsExistingSession(Long ptId, LocalDate date, ResolvedSlot slot,
                                           Long excludeSessionId) {
        return trainingSessionRepository
                .findByPtProfile_IdAndSessionDateAndStatusIn(ptId, date, HOLDING_STATUSES).stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .filter(s -> s.getPtSlotStart() != null)
                .anyMatch(s -> blocks(slot.startTime(), slot.endTime(),
                        s.getPtSlotStart(), s.getPtSlotEnd()));
    }

    /**
     * Buổi đã có (từ {@code otherStart} tới {@code otherEnd}) có chặn khoảng
     * đang xét không.
     *
     * <p>{@code otherEnd} null là dữ liệu hỏng — không bao giờ xảy ra ở đường ghi
     * bình thường vì {@code applyPt} luôn đặt cả hai mốc. Khi gặp thì CHẶN dựa
     * trên mỗi giờ bắt đầu, không phải bỏ qua: một dòng thiếu dữ liệu mà được
     * coi như chỗ trống thì lỗi dữ liệu biến thành lỗi đặt trùng, và PT là người
     * lãnh hậu quả.
     */
    private boolean blocks(LocalTime start, LocalTime end, LocalTime otherStart, LocalTime otherEnd) {
        if (otherEnd == null) {
            return !otherStart.isBefore(start) && otherStart.isBefore(end);
        }
        return slotResolver.overlaps(start, end, otherStart, otherEnd);
    }

    /**
     * Ô lưới bắt đầu lúc {@code slotStart} đã bị chiếm chưa — bản cho FE, nơi
     * mỗi ô là MỘT slot nên so bằng giờ bắt đầu là đủ và rẻ hơn.
     */
    public boolean isTaken(Long ptId, LocalDate date, LocalTime slotStart, Long excludeSessionId) {
        return trainingSessionRepository
                .findByPtProfile_IdAndSessionDateAndStatusIn(ptId, date, HOLDING_STATUSES).stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .filter(s -> s.getPtSlotStart() != null)
                .anyMatch(s -> s.getPtSlotEnd() == null
                        ? slotStart.equals(s.getPtSlotStart())
                        : !slotStart.isBefore(s.getPtSlotStart())
                                && slotStart.isBefore(s.getPtSlotEnd()));
    }
}
