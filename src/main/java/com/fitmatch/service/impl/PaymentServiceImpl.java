package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.config.PaymentProperties;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional
    public PaymentOrderResponse createOrder(Booking booking) {
        if (booking.getPayableAmount() == null
                || booking.getPayableAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ERROR, "Booking has no payable amount");
        }
        // Idempotent: một booking chỉ có một payment order.
        PaymentOrder existing = paymentOrderRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (existing != null) {
            return PaymentOrderResponse.of(existing);
        }

        String refCode = "FM" + booking.getId() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.builder()
                .booking(booking)
                .refCode(refCode)
                .amount(booking.getPayableAmount())
                .status(PaymentStatus.PENDING)
                .qrContent(buildVietQrUrl(booking.getPayableAmount().toBigInteger().toString(), refCode))
                .expiresAt(LocalDateTime.now().plusHours(paymentProperties.getPayment().getOrderTtlHours()))
                .build());
        log.info("Created payment order {} (ref {}) for booking {}", order.getId(), refCode, booking.getId());
        return PaymentOrderResponse.of(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getForCustomer(Long bookingId, String customerUsername) {
        PaymentOrder order = paymentOrderRepository
                .findByBooking_IdAndBooking_Customer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order for booking", bookingId));
        return PaymentOrderResponse.of(order);
    }

    /** VietQR quick-link (img.vietqr.io) — FE render ảnh QR; addInfo = refCode để Casso đối soát. */
    private String buildVietQrUrl(String amount, String refCode) {
        PaymentProperties.Vietqr v = paymentProperties.getVietqr();
        String name = URLEncoder.encode(v.getAccountName() != null ? v.getAccountName() : "", StandardCharsets.UTF_8);
        return "https://img.vietqr.io/image/" + v.getBankBin() + "-" + v.getAccountNo() + "-"
                + v.getTemplate() + ".png?amount=" + amount + "&addInfo=" + refCode + "&accountName=" + name;
    }
}
