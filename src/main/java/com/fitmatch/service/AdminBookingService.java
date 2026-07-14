package com.fitmatch.service;

import com.fitmatch.dto.booking.BookingResponse;

/**
 * Thao tác booking cấp quản trị (UC-036, UC-045).
 */
public interface AdminBookingService {

    /**
     * UC-036: ghi nhận tiền đã giữ cho booking -> PENDING_GYM (UC-037).
     * Tạm thời là thao tác Admin; phase payment sẽ gọi tự động từ webhook
     * VietQR/Casso khi đối soát thành công.
     */
    BookingResponse confirmPaymentHold(Long bookingId, String actorUsername);

    /** UC-050: admin hiệu chỉnh bản ghi điểm danh/hoàn tất kèm lý do + audit. */
    BookingResponse correctAttendance(Long bookingId,
                                      com.fitmatch.dto.booking.CorrectAttendanceRequest request,
                                      String actorUsername);
}
