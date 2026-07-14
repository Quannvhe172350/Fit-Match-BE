package com.fitmatch.scheduler;

import com.fitmatch.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-054: đơn thanh toán PENDING quá hạn -> EXPIRED, booking chờ thanh toán bị
 * đóng để giải phóng slot. Chạy 5 phút/lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderExpiryJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.expiry-job-delay-ms:300000}")
    public void expireOverdueOrders() {
        try {
            int cancelled = paymentService.expireOverdueOrders();
            if (cancelled > 0) {
                log.info("Payment expiry job cancelled {} unpaid booking(s)", cancelled);
            }
        } catch (Exception e) {
            log.error("Payment expiry job failed", e);
        }
    }
}
