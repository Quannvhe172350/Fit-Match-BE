package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.AdminBookingService;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.support.BookingPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookingServiceImpl implements AdminBookingService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentHandler bookingPaymentHandler;
    private final AuditService auditService;
    private final com.fitmatch.service.support.AttendanceSupport attendanceSupport;

    @Override
    @Transactional
    public BookingResponse confirmPaymentHold(Long bookingId, String actorUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        // Giữ tiền vào ví nền tảng + chuyển PENDING_PAYMENT -> PENDING_GYM (chặn nếu sai trạng thái).
        java.math.BigDecimal amount = booking.getPayableAmount() != null
                ? booking.getPayableAmount() : java.math.BigDecimal.ZERO;
        bookingPaymentHandler.onPaymentConfirmed(booking, amount, "admin:" + actorUsername);
        bookingRepository.save(booking);

        auditService.record(AuditActions.BOOKING_PAYMENT_HOLD, "Booking", bookingId,
                "Payment hold confirmed by " + actorUsername);
        log.info("Booking {} payment hold confirmed by {}", bookingId, actorUsername);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse correctAttendance(Long bookingId,
                                             com.fitmatch.dto.booking.CorrectAttendanceRequest request,
                                             String actorUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        attendanceSupport.correct(booking, request, "admin " + actorUsername);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }
}
