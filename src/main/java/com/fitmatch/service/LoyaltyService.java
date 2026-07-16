package com.fitmatch.service;

import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.loyalty.LoyaltyBalanceResponse;
import com.fitmatch.entity.Booking;
import org.springframework.data.domain.Pageable;

/**
 * Điểm thưởng (UC-073). Tích điểm khi hoàn tất buổi tập; tiêu điểm để giảm giá
 * booking (loại trừ lẫn nhau với voucher). Tỷ lệ cố định ở LoyaltyServiceImpl.
 */
public interface LoyaltyService {

    LoyaltyBalanceResponse balance(String username, Pageable pageable);

    /** Tích điểm khi booking hoàn tất theo số tiền đã trả (không ném lỗi vào luồng chính). */
    void earnFromBooking(Booking booking);

    /** UC-073: dùng điểm cho booking DRAFT — validate + lưu discount (gỡ voucher nếu có). */
    BookingResponse applyToBooking(String customerUsername, Long bookingId, int points);

    BookingResponse removeFromBooking(String customerUsername, Long bookingId);

    /** Chốt lại discount điểm trên booking theo total hiện tại. */
    void recomputeDiscount(Booking booking);

    /** Trừ điểm thật khi checkout (khoá tài khoản + kiểm tra số dư). */
    void consumeAtCheckout(Booking booking);

    /**
     * Hoàn lại số điểm đã REDEEM khi booking bị hủy/từ chối sau checkout mà không
     * hoàn tất dịch vụ (UC-073). Không ném lỗi vào luồng chính. Idempotency do
     * caller đảm bảo qua cờ {@code promoReleased}.
     */
    void refundToBooking(Booking booking);
}
