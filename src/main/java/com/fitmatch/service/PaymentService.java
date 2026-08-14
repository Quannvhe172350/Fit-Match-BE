package com.fitmatch.service;

import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.entity.Ticket;

/**
 * Đơn thanh toán VietQR (UC-052) và trạng thái thanh toán (UC-054).
 * Mỗi vé có tối đa một đơn; ref_code dạng FM&lt;id&gt;&lt;6 hex&gt; để Casso đối soát.
 */
public interface PaymentService {

    /** Tạo đơn thanh toán + nội dung VietQR cho một vé (payable > 0). Idempotent theo vé. */
    PaymentOrderResponse createOrderForTicket(Ticket ticket);

    /** Xem đơn thanh toán của vé (chủ vé) — FE poll 5s ở màn QR. */
    PaymentOrderResponse getForTicketCustomer(Long ticketId, String customerUsername);

    /** Đóng đơn PENDING khi khách tự huỷ vé trước khi trả tiền. */
    void cancelTicketOrderIfPending(Long ticketId);

    /** UC-054: đánh dấu EXPIRED các đơn PENDING quá hạn; trả về số vé bị huỷ kèm. */
    int expireOverdueOrders();
}
