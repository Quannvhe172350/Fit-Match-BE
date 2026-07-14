package com.fitmatch.service;

import com.fitmatch.dto.booking.BookingResponse;

/** Thao tác của PT trên buổi tập được gán (UC-046). */
public interface PtBookingService {

    /** UC-046: PT ghi nhận check-in cho khách của buổi tập mình phụ trách. */
    BookingResponse checkIn(String ptUsername, Long bookingId);
}
