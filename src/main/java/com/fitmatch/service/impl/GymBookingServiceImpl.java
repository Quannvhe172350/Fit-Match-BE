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
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.GymBookingService;
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
    private final BookingEligibilityChecker bookingEligibilityChecker;
    private final BookingLifecycle bookingLifecycle;
    private final AuditService auditService;

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
        // Phase payment: REJECTED của booking đã giữ tiền sẽ kích hoạt hoàn tiền (UC-055).
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
