package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.AdminBookingService;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.support.BookingLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookingServiceImpl implements AdminBookingService {

    private final BookingRepository bookingRepository;
    private final BookingLifecycle bookingLifecycle;
    private final AuditService auditService;

    @Override
    @Transactional
    public BookingResponse confirmPaymentHold(Long bookingId, String actorUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        // BookingLifecycle tự chặn nếu không ở PENDING_PAYMENT.
        bookingLifecycle.transition(booking, BookingStatus.PENDING_GYM,
                "Payment hold confirmed by " + actorUsername + " - routed to gym");
        bookingRepository.save(booking);

        auditService.record(AuditActions.BOOKING_PAYMENT_HOLD, "Booking", bookingId,
                "Payment hold confirmed by " + actorUsername);
        log.info("Booking {} payment hold confirmed by {}", bookingId, actorUsername);
        return BookingResponse.of(booking);
    }
}
