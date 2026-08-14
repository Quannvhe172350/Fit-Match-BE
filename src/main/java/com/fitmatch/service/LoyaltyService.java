package com.fitmatch.service;

import com.fitmatch.dto.loyalty.LoyaltyBalanceResponse;
import com.fitmatch.entity.Ticket;
import org.springframework.data.domain.Pageable;

/**
 * Điểm thưởng (UC-073). Câu 35: TÍCH điểm khi thanh toán vé thành công (mô hình
 * cũ tích khi hoàn tất buổi tập). Câu 14: TIÊU điểm là dùng toàn bộ số khả
 * dụng, cap ở số tiền phải trả — không còn ô nhập số điểm.
 * Tỷ lệ quy đổi cố định ở LoyaltyServiceImpl.
 */
public interface LoyaltyService {

    LoyaltyBalanceResponse balance(String username, Pageable pageable);

    /** Số điểm khả dụng của khách — dùng cho toggle "dùng toàn bộ điểm". */
    int availablePoints(String username);

    /** Trừ điểm thật khi mua vé (khoá tài khoản + kiểm tra số dư). */
    void consumeForTicket(Ticket ticket);

    /** Câu 35: tích điểm khi thanh toán vé thành công. Không ném lỗi vào luồng chính. */
    void earnFromTicket(Ticket ticket);

    /**
     * Hoàn lại điểm đã REDEEM khi vé bị huỷ hoặc hoàn toàn bộ. Không ném lỗi vào
     * luồng chính; idempotency do caller giữ bằng cờ {@code promoReleased}.
     */
    void refundToTicket(Ticket ticket);
}
