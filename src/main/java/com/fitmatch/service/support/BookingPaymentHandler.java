package com.fitmatch.service.support;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Logic dùng chung khi tiền booking đã được xác nhận (UC-036/053/057): giữ tiền
 * vào ví nền tảng của Gym rồi chuyển booking PENDING_PAYMENT -> PENDING_GYM.
 * Gọi từ webhook Casso (tự động) và từ endpoint Admin xác nhận thủ công.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentHandler {

    private final WalletService walletService;
    private final BookingLifecycle bookingLifecycle;
    private final SettlementService settlementService;

    @Transactional
    public void onPaymentConfirmed(Booking booking, BigDecimal paidAmount, String source) {
        walletService.getOrCreate(booking.getGymProfile());
        walletService.hold(booking.getGymProfile().getId(), booking.getId(), paidAmount);
        settlementService.markHeld(booking);
        bookingLifecycle.transition(booking, BookingStatus.PENDING_GYM,
                "Payment confirmed (" + source + ") - funds held, routed to gym");
        log.info("Booking {} payment confirmed via {} (held {})", booking.getId(), source, paidAmount);
    }
}
