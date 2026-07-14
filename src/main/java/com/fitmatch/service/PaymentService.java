package com.fitmatch.service;

import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.entity.Booking;

/**
 * Đơn thanh toán VietQR (UC-052) và trạng thái thanh toán (UC-054).
 */
public interface PaymentService {

    /** UC-052: tạo đơn thanh toán + nội dung VietQR cho booking (payable > 0). */
    PaymentOrderResponse createOrder(Booking booking);

    /** Xem đơn thanh toán của booking (chủ booking). */
    PaymentOrderResponse getForCustomer(Long bookingId, String customerUsername);

    /** UC-054: đóng đơn PENDING khi booking bị hủy/từ chối trước khi trả tiền. */
    void cancelOrderIfPending(Long bookingId);

    /** UC-054: đánh dấu EXPIRED các đơn PENDING quá hạn; trả về số booking bị hủy kèm. */
    int expireOverdueOrders();
}
