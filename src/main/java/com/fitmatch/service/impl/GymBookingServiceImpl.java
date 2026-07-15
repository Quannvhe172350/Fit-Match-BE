package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.GymBookingService;
import com.fitmatch.service.PaymentService;
import com.fitmatch.service.RefundService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.support.BookingEligibilityChecker;
import com.fitmatch.service.support.BookingLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymBookingServiceImpl implements GymBookingService {

    private final BookingRepository bookingRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final BookingEligibilityChecker bookingEligibilityChecker;
    private final BookingLifecycle bookingLifecycle;
    private final AuditService auditService;
    private final RefundService refundService;
    private final SettlementService settlementService;
    private final PaymentService paymentService;
    private final com.fitmatch.service.PackageUsageService packageUsageService;
    private final com.fitmatch.service.support.AttendanceSupport attendanceSupport;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    private final com.fitmatch.service.LoyaltyService loyaltyService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(String gymUsername, BookingStatus status, Pageable pageable) {
        BookingStatus effective = status != null ? status : BookingStatus.PENDING_GYM;
        return PageResponse.of(
                bookingRepository.findByGymProfile_User_UsernameAndStatus(gymUsername, effective, pageable),
                BookingResponse::of);
    }

    @Override
    @Transactional
    public BookingResponse accept(String gymUsername, Long bookingId, Long ptId) {
        Booking booking = requireOwned(gymUsername, bookingId);
        if (ptId != null) {
            applyPt(gymUsername, booking, ptId);
        } else if (booking.getPtProfile() != null) {
            // Khách đã đề xuất PT — xác nhận lại trước khi chốt.
            applyPt(gymUsername, booking, booking.getPtProfile().getId());
        }
        bookingLifecycle.transition(booking, BookingStatus.CONFIRMED,
                "Accepted by gym " + gymUsername);
        bookingRepository.save(booking);
        notificationDispatcher.bookingAccepted(booking);
        auditService.record(AuditActions.BOOKING_ACCEPT, "Booking", bookingId,
                "Accepted by gym " + gymUsername);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse reject(String gymUsername, Long bookingId, String reason) {
        Booking booking = requireOwned(gymUsername, bookingId);
        bookingLifecycle.transition(booking, BookingStatus.REJECTED,
                "Rejected by gym: " + reason);
        bookingRepository.save(booking);
        // UC-038/055: gym từ chối booking đã giữ tiền -> tự mở yêu cầu hoàn toàn bộ.
        refundService.autoCreate(booking, "Gym rejected booking: " + reason);
        paymentService.cancelOrderIfPending(bookingId);
        notificationDispatcher.bookingRejected(booking, reason);
        auditService.record(AuditActions.BOOKING_REJECT, "Booking", bookingId,
                "Rejected by gym " + gymUsername + ": " + reason);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse assignPt(String gymUsername, Long bookingId, Long ptId) {
        Booking booking = requireOwned(gymUsername, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_GYM
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "PT can only be assigned while the booking is PENDING_GYM or CONFIRMED (current: "
                            + booking.getStatus() + ")");
        }
        applyPt(gymUsername, booking, ptId);
        bookingRepository.save(booking);
        auditService.record(AuditActions.BOOKING_PT_ASSIGN, "Booking", bookingId,
                "PT " + ptId + " assigned by gym " + gymUsername);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse reschedule(String gymUsername, Long bookingId,
                                      java.time.LocalDateTime startAt, java.time.LocalDateTime endAt) {
        Booking booking = requireOwned(gymUsername, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_GYM
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a PENDING_GYM or CONFIRMED booking can be rescheduled (current: "
                            + booking.getStatus() + ")");
        }
        if (booking.getPtProfile() != null) {
            ptProfileRepository.lockById(booking.getPtProfile().getId());
        }
        if (booking.getGymBranch() != null) {
            gymBranchRepository.lockById(booking.getGymBranch().getId());
        }
        List<String> issues = bookingEligibilityChecker.rescheduleIssues(booking, startAt, endAt);
        if (!issues.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot reschedule: " + String.join("; ", issues));
        }
        String note = "Rescheduled by gym from " + booking.getStartAt() + " to " + startAt;
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        bookingLifecycle.recordNote(booking, note);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancel(String gymUsername, Long bookingId, String reason) {
        Booking booking = requireOwned(gymUsername, bookingId);
        bookingLifecycle.transition(booking, BookingStatus.CANCELLED,
                "Cancelled by gym: " + reason);
        bookingRepository.save(booking);
        // UC-042/055: gym chủ động hủy -> khách được mở yêu cầu hoàn toàn bộ.
        refundService.autoCreate(booking, "Gym cancelled booking: " + reason);
        paymentService.cancelOrderIfPending(bookingId);
        notificationDispatcher.bookingCancelledByGym(booking, reason);
        auditService.record(AuditActions.BOOKING_CANCEL_BY_GYM, "Booking", bookingId,
                "Cancelled by gym " + gymUsername + ": " + reason);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse markNoShow(String gymUsername, Long bookingId) {
        Booking booking = requireOwned(gymUsername, bookingId);
        if (booking.getStartAt() == null || booking.getStartAt().isAfter(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "No-show can only be recorded after the session start time");
        }
        bookingLifecycle.transition(booking, BookingStatus.NO_SHOW,
                "Marked as no-show by gym " + gymUsername);
        // UC-043: khách không đến -> theo chính sách nền tảng, tiền giữ được chuyển
        // cho Gym (qua pending settlement, vẫn có holding period để khiếu nại).
        settlementService.settleAfterFulfillment(booking, "no-show");
        // UC-049: no-show vẫn tiêu thụ buổi của gói (chính sách nền tảng).
        packageUsageService.onBookingFulfilled(booking);
        bookingRepository.save(booking);
        notificationDispatcher.bookingNoShow(booking);
        auditService.record(AuditActions.BOOKING_NO_SHOW, "Booking", bookingId,
                "No-show recorded by gym " + gymUsername);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse complete(String gymUsername, Long bookingId) {
        Booking booking = requireOwned(gymUsername, bookingId);
        if (booking.getStartAt() == null || booking.getStartAt().isAfter(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Booking can only be completed after the session start time");
        }
        bookingLifecycle.transition(booking, BookingStatus.COMPLETED,
                "Completed by gym " + gymUsername);
        booking.setCompletedAt(java.time.LocalDateTime.now());
        // UC-049/058: hoàn tất -> tiền giữ chuyển sang pending settlement.
        settlementService.settleAfterFulfillment(booking, "session completed");
        // UC-049: kích hoạt gói (booking mua gói) hoặc trừ một buổi (buổi thuộc gói).
        packageUsageService.onBookingFulfilled(booking);
        // UC-073: tích điểm thưởng theo số tiền đã trả.
        loyaltyService.earnFromBooking(booking);
        bookingRepository.save(booking);
        notificationDispatcher.bookingCompleted(booking);
        auditService.record(AuditActions.BOOKING_COMPLETE, "Booking", bookingId,
                "Completed by gym " + gymUsername);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse checkIn(String gymUsername, Long bookingId) {
        Booking booking = requireOwned(gymUsername, bookingId);
        attendanceSupport.checkIn(booking, "gym " + gymUsername);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse correctAttendance(String gymUsername, Long bookingId,
                                             com.fitmatch.dto.booking.CorrectAttendanceRequest request) {
        Booking booking = requireOwned(gymUsername, bookingId);
        attendanceSupport.correct(booking, request, "gym " + gymUsername);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    /** Khoá PT + kiểm tra assignment/lịch/trùng chỗ rồi gán vào booking (UC-039). */
    private void applyPt(String gymUsername, Booking booking, Long ptId) {
        PtProfile pt = ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        ptProfileRepository.lockById(ptId);
        List<String> issues = bookingEligibilityChecker.ptIssues(booking, ptId);
        if (!issues.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "PT cannot take this booking: " + String.join("; ", issues));
        }
        booking.setPtProfile(pt);
        log.info("Booking {} assigned PT {} by gym {}", booking.getId(), ptId, gymUsername);
    }

    private Booking requireOwned(String gymUsername, Long bookingId) {
        return bookingRepository.findByIdAndGymProfile_User_Username(bookingId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }
}
