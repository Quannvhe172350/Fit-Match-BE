package com.fitmatch.service.impl;

import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.PtBookingService;
import com.fitmatch.service.support.AttendanceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PtBookingServiceImpl implements PtBookingService {

    private final BookingRepository bookingRepository;
    private final AttendanceSupport attendanceSupport;

    @Override
    @Transactional
    public BookingResponse checkIn(String ptUsername, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(b -> b.getPtProfile() != null
                        && b.getPtProfile().getUser().getUsername().equals(ptUsername))
                // 404 thay vì 403: không tiết lộ booking của người khác.
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        attendanceSupport.checkIn(booking, "pt " + ptUsername);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }
}
