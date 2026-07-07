package com.fitmatch.service;

import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.CreateBookingRequest;

/**
 * Booking phía Customer (UC-031, UC-032). Các bước checkout/cancel/reschedule
 * bổ sung ở các UC tiếp theo trong cùng module.
 */
public interface BookingService {

    /** UC-031: tạo booking nháp — cần tối thiểu một đích để xác định Gym chịu trách nhiệm. */
    BookingResponse createDraft(String customerUsername, CreateBookingRequest request);

    /** UC-032: cập nhật lựa chọn service/package/PT/branch/time khi còn DRAFT. */
    BookingResponse updateSelection(String customerUsername, Long bookingId, CreateBookingRequest request);
}
