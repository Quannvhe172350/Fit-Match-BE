package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.PtAvailability;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtAvailabilityRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra một khung giờ PT có đặt được không — nửa thứ hai của
 * {@code BookingEligibilityChecker} cũ, tách hẳn ra vì buổi tập KHÔNG có PT
 * không được chạm tới lớp này (vé cả ngày thì không có gì để kiểm về giờ giấc).
 *
 * <p>Bốn điều kiện, đúng thứ tự rẻ tiền trước:
 * <ol>
 *   <li>PT đang ACTIVE;</li>
 *   <li>PT được phân công vào đúng chi nhánh của vé (câu 24 — assignment giờ
 *       chỉ còn đích chi nhánh);</li>
 *   <li>PT đã khai đúng khung giờ đó cho đúng ngày đó (pt_availabilities);</li>
 *   <li>khung giờ chưa bị buổi tập nào khác chiếm.</li>
 * </ol>
 * Giờ mở cửa chi nhánh và sức chứa không còn tham gia (câu 26/27/28).
 */
@Component
@RequiredArgsConstructor
public class PtSlotValidator {

    /** Buổi đang giữ chỗ của PT — buổi đã huỷ thì trả lại khung giờ. */
    public static final Set<SessionStatus> HOLDING_STATUSES =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    private final PtProfileRepository ptProfileRepository;
    private final PtAssignmentRepository ptAssignmentRepository;
    private final PtAvailabilityRepository ptAvailabilityRepository;
    private final TrainingSessionRepository trainingSessionRepository;

    /**
     * Ném 409 kèm mọi vi phạm nếu không đặt được; trả về khung giờ đã khai của PT
     * để caller chép {@code endTime} vào buổi tập (giờ kết thúc do PT quyết, khách
     * chỉ chọn giờ bắt đầu).
     *
     * @param excludeSessionId buổi đang được sửa — loại trừ chính nó khi đổi khung
     *                         giờ trong cùng ngày; null khi đặt mới
     */
    public PtAvailability resolveSlot(Long ptId, Long branchId, LocalDate date,
                                      LocalTime slotStart, Long excludeSessionId) {
        List<String> reasons = new ArrayList<>();

        PtProfile pt = ptProfileRepository.findById(ptId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "PT not found: " + ptId));
        if (pt.getStatus() != PtStatus.ACTIVE) {
            reasons.add("PT hiện không nhận lịch (trạng thái: " + pt.getStatus() + ")");
        }
        if (!ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(ptId, branchId)) {
            reasons.add("PT không phụ trách chi nhánh này");
        }

        PtAvailability slot = ptAvailabilityRepository
                .findByPtProfile_IdAndSlotDateAndStartTime(ptId, date, slotStart)
                .orElse(null);
        if (slot == null) {
            reasons.add("PT không khai khung giờ " + slotStart + " ngày " + date);
        } else if (isTaken(ptId, date, slotStart, excludeSessionId)) {
            reasons.add("Khung giờ này của PT đã có người đặt");
        }

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể chọn PT: " + String.join("; ", reasons));
        }
        return slot;
    }

    /** Dùng cho lưới ngày x giờ ở FE: ô đã có người đặt thì hiển thị mờ. */
    public boolean isTaken(Long ptId, LocalDate date, LocalTime slotStart, Long excludeSessionId) {
        return trainingSessionRepository
                .findByPtProfile_IdAndSessionDateAndStatusIn(ptId, date, HOLDING_STATUSES).stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .anyMatch(s -> slotStart.equals(s.getPtSlotStart()));
    }
}
