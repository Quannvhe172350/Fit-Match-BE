package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.voucher.VoucherRequest;
import com.fitmatch.dto.voucher.VoucherResponse;
import com.fitmatch.entity.Booking;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Voucher/khuyến mãi (UC-073). Admin cấu hình; khách áp vào booking nháp; hệ
 * thống chốt số giảm và tiêu thụ lượt khi checkout.
 */
public interface VoucherService {

    // ----- Admin -----
    VoucherResponse create(VoucherRequest request);

    VoucherResponse update(Long id, VoucherRequest request);

    VoucherResponse setActive(Long id, boolean active);

    PageResponse<VoucherResponse> list(Pageable pageable);

    // ----- Customer -----
    /** UC-073: áp mã vào booking DRAFT — validate + lưu voucher & discountAmount. */
    BookingResponse applyToBooking(String customerUsername, Long bookingId, String code);

    /** Gỡ voucher khỏi booking DRAFT. */
    BookingResponse removeFromBooking(String customerUsername, Long bookingId);

    // ----- Dùng nội bộ khi checkout -----
    /** Số tiền giảm cho voucher trên một tổng giá trị booking (0 nếu không đủ điều kiện). */
    BigDecimal computeDiscount(com.fitmatch.entity.Voucher voucher, BigDecimal total);

    /** Chốt lại discount trên booking theo voucher đã áp (đề phòng đổi lựa chọn). */
    void recomputeDiscount(Booking booking);

    /** Tiêu thụ một lượt voucher khi checkout (khoá + tăng usedCount, kiểm tra giới hạn). */
    void consumeAtCheckout(Booking booking);
}
