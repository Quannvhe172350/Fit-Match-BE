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

    /**
     * UC-035: checkout — validate eligibility (UC-033), chốt giá (UC-034) rồi chuyển
     * DRAFT -> PENDING_PAYMENT (hoặc PENDING_GYM nếu số phải trả = 0).
     */
    BookingResponse checkout(String customerUsername, Long bookingId);

    /** UC-041: customer dời lịch (PENDING_GYM/CONFIRMED) sang khung giờ hợp lệ mới. */
    BookingResponse reschedule(String customerUsername, Long bookingId, java.time.LocalDateTime startAt,
                               java.time.LocalDateTime endAt);

    /**
     * UC-042: customer hủy booking; hủy CONFIRMED trong cửa sổ mất phí
     * (freeCancellationHours) bị đánh dấu lateCancellation (UC-043).
     */
    BookingResponse cancel(String customerUsername, Long bookingId, String reason);

    /** UC-046: customer tự check-in buổi tập CONFIRMED trong cửa sổ cho phép. */
    BookingResponse checkIn(String customerUsername, Long bookingId);
}
