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
}
