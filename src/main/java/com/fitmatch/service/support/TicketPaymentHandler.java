package com.fitmatch.service.support;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Ticket;
import com.fitmatch.service.LoyaltyService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tiền vé đã được xác nhận: giữ vào ví nền tảng của Gym rồi kích hoạt vé.
 * Bản cho mô hình vé của {@link BookingPaymentHandler}.
 *
 * <p>Khác biệt lớn nhất: KHÔNG còn nhánh PENDING_GYM. Gym không duyệt lịch nữa
 * (quyết định #7) nên vé đi thẳng PENDING_PAYMENT -> ACTIVE và khách đặt được
 * ngày ngay lập tức.
 *
 * <p>Câu 35: đây cũng là mốc tích điểm thưởng — trước đây điểm chỉ cộng khi
 * hoàn tất buổi tập.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketPaymentHandler {

    private final WalletService walletService;
    private final TicketLifecycle ticketLifecycle;
    private final SettlementService settlementService;
    private final LoyaltyService loyaltyService;
    private final NotificationDispatcher notificationDispatcher;

    @Transactional
    public void onPaymentConfirmed(Ticket ticket, BigDecimal paidAmount, String source) {
        walletService.getOrCreate(ticket.getGymProfile());
        walletService.holdForTicket(ticket.getGymProfile().getId(), ticket.getId(), paidAmount);
        settlementService.markTicketHeld(ticket);
        activate(ticket, "Payment confirmed (" + source + ") - funds held");
        log.info("Ticket {} payment confirmed via {} (held {})", ticket.getId(), source, paidAmount);
    }

    /**
     * Câu 14: điểm thưởng/voucher phủ hết tổng tiền -> không có gì để chuyển
     * khoản, vé ACTIVE ngay tại thời điểm mua và FE không hiện QR. Không có
     * đồng nào vào ví nên settlementStatus giữ nguyên NONE — vé này về sau
     * cũng không có gì để giải ngân hay hoàn.
     */
    @Transactional
    public void onFullyDiscounted(Ticket ticket) {
        activate(ticket, "Fully covered by loyalty points/voucher");
        log.info("Ticket {} activated without payment (payable = 0)", ticket.getId());
    }

    private void activate(Ticket ticket, String reason) {
        ticketLifecycle.transition(ticket, TicketStatus.ACTIVE, reason);
        ticket.setPurchasedAt(LocalDateTime.now());
        loyaltyService.earnFromTicket(ticket);
        notificationDispatcher.ticketPaid(ticket);
    }
}
