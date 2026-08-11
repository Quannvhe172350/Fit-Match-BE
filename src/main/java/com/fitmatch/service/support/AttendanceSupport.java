package com.fitmatch.service.support;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.booking.CorrectAttendanceRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Logic điểm danh dùng chung (UC-046/050) cho customer/gym/PT/admin — guard
 * cửa sổ thời gian, chống check-in trùng, và hiệu chỉnh có audit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSupport {

    /** Cho phép check-in sớm tối đa 30 phút trước giờ bắt đầu. */
    private static final int EARLY_CHECK_IN_MINUTES = 30;

    /** Trạng thái tiền cho phép hiệu chỉnh — sau khi giải ngân/hoàn thì bất biến. */
    private static final Set<SettlementStatus> CORRECTABLE_SETTLEMENTS =
            Set.of(SettlementStatus.NONE, SettlementStatus.HELD, SettlementStatus.PENDING_RELEASE);

    private final BookingLifecycle bookingLifecycle;
    private final AuditService auditService;

    /**
     * UC-046: ghi nhận check-in — CONFIRMED, trong cửa sổ [startAt-30', endAt], chưa check-in.
     * TẠM: chặn "sớm hơn 30 phút" đang bị comment để test nhanh (xem bên dưới), nhớ mở lại.
     */
    public void checkIn(Booking booking, String actorLabel) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Check-in requires a CONFIRMED booking (current: " + booking.getStatus() + ")");
        }
        if (booking.getCheckedInAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Booking is already checked in");
        }
        LocalDateTime now = LocalDateTime.now();
        // TODO(test): tạm bỏ chặn check-in sớm để khỏi phải chờ tới sát giờ khi test.
        // MỞ LẠI trước khi merge — bỏ comment nguyên khối dưới đây là xong.
        // if (booking.getStartAt() == null
        //         || now.isBefore(booking.getStartAt().minusMinutes(EARLY_CHECK_IN_MINUTES))) {
        //     throw new BusinessException(ErrorCode.INVALID_STATE,
        //             "Check-in opens " + EARLY_CHECK_IN_MINUTES + " minutes before the session start");
        // }
        if (booking.getEndAt() != null && now.isAfter(booking.getEndAt())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "The session has already ended - ask the gym to correct attendance instead");
        }
        booking.setCheckedInAt(now);
        bookingLifecycle.recordNote(booking, "Checked in by " + actorLabel);
        auditService.record(AuditActions.BOOKING_CHECK_IN, "Booking", booking.getId(),
                "Checked in by " + actorLabel);
        log.info("Booking {} checked in by {}", booking.getId(), actorLabel);
    }

    /**
     * UC-050: hiệu chỉnh mốc check-in và/hoặc đổi COMPLETED <-> NO_SHOW kèm lý do.
     * Chặn khi tiền đã giải ngân/đang xử lý hoàn (bản ghi tài chính bất biến).
     */
    public void correct(Booking booking, CorrectAttendanceRequest request, String actorLabel) {
        if (!CORRECTABLE_SETTLEMENTS.contains(booking.getSettlementStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Attendance cannot be corrected after funds were released/refunded (settlement: "
                            + booking.getSettlementStatus() + ")");
        }
        boolean checkInChanged = false;
        if (Boolean.TRUE.equals(request.getClearCheckIn())) {
            booking.setCheckedInAt(null);
            checkInChanged = true;
        } else if (request.getCheckedInAt() != null) {
            booking.setCheckedInAt(request.getCheckedInAt());
            checkInChanged = true;
        }
        boolean statusChanged = request.getStatus() != null && request.getStatus() != booking.getStatus();
        if (statusChanged) {
            // Override tự ghi history from->to kèm lý do.
            bookingLifecycle.overrideForCorrection(booking, request.getStatus(),
                    "Attendance corrected by " + actorLabel + ": " + request.getReason());
            // P2-B8: đồng bộ mốc hoàn tất với trạng thái sau hiệu chỉnh — tránh COMPLETED
            // thiếu completedAt (NO_SHOW->COMPLETED) hoặc NO_SHOW còn giữ completedAt cũ.
            if (request.getStatus() == BookingStatus.COMPLETED) {
                if (booking.getCompletedAt() == null) {
                    booking.setCompletedAt(LocalDateTime.now());
                }
            } else {
                booking.setCompletedAt(null);
            }
        }
        if (!checkInChanged && !statusChanged) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Nothing to correct - provide checkedInAt, clearCheckIn or status");
        }
        if (checkInChanged && !statusChanged) {
            bookingLifecycle.recordNote(booking,
                    "Attendance record corrected by " + actorLabel + ": " + request.getReason());
        }
        auditService.record(AuditActions.ATTENDANCE_CORRECT, "Booking", booking.getId(),
                "Corrected by " + actorLabel + ": " + request.getReason());
    }
}
