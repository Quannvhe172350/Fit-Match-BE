package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.config.PaymentProperties;
import com.fitmatch.dto.payment.PaymentOrderResponse;
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
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    private final com.fitmatch.service.support.TicketLifecycle ticketLifecycle;
    private final com.fitmatch.service.support.TicketPromotionReleaser ticketPromotionReleaser;

    @Override
    @Transactional
    public PaymentOrderResponse createOrderForTicket(com.fitmatch.entity.Ticket ticket) {
        if (ticket.getPayableAmount() == null
                || ticket.getPayableAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ERROR, "Ticket has no payable amount");
        }
        // Idempotent: một vé chỉ có một payment order (unique ticket_id ở V73).
        PaymentOrder existing = paymentOrderRepository.findByTicket_Id(ticket.getId()).orElse(null);
        if (existing != null) {
            return PaymentOrderResponse.of(existing);
        }

        // Giữ nguyên dạng FM<id><6 hex>: PaymentWebhookServiceImpl dò nội dung
        // chuyển khoản bằng regex FM\d+[0-9A-F]{6}. refCode là UNIQUE nên vé #5
        // và booking #5 không đụng nhau dù cùng tiền tố.
        String refCode = "FM" + ticket.getId() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.builder()
                .ticket(ticket)
                .refCode(refCode)
                .amount(ticket.getPayableAmount())
                .status(PaymentStatus.PENDING)
                .qrContent(buildVietQrUrl(ticket.getPayableAmount().toBigInteger().toString(), refCode))
                .expiresAt(LocalDateTime.now().plusHours(paymentProperties.getPayment().getOrderTtlHours()))
                .build());
        log.info("Created payment order {} (ref {}) for ticket {}", order.getId(), refCode, ticket.getId());
        return PaymentOrderResponse.of(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getForTicketCustomer(Long ticketId, String customerUsername) {
        PaymentOrder order = paymentOrderRepository
                .findByTicket_IdAndTicket_Customer_Username(ticketId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order for ticket", ticketId));
        return PaymentOrderResponse.of(order);
    }

    @Override
    @Transactional
    public void cancelTicketOrderIfPending(Long ticketId) {
        paymentOrderRepository.findByTicket_Id(ticketId)
                .filter(o -> o.getStatus() == PaymentStatus.PENDING)
                .ifPresent(o -> {
                    o.setStatus(PaymentStatus.CANCELLED);
                    paymentOrderRepository.save(o);
                    log.info("Payment order {} cancelled (ticket {} closed before payment)",
                            o.getId(), ticketId);
                });
    }


    // D-7 (chính sách chốt 2026-07-17): KHÔNG có luồng retry cho đơn EXPIRED —
    // vé bị huỷ kèm hoàn promo, khách chậm chuyển khoản thì mua vé mới
    // (TTL 24h đủ rộng; giữ chỗ vô hạn sẽ khóa slot của khách khác).
    @Override
    @Transactional
    public int expireOverdueOrders() {
        var overdue = paymentOrderRepository
                .findByStatusAndExpiresAtBefore(PaymentStatus.PENDING, LocalDateTime.now());
        int cancelledTickets = 0;
        for (PaymentOrder order : overdue) {
            order.setStatus(PaymentStatus.EXPIRED);
            paymentOrderRepository.save(order);
            cancelledTickets += expireTicketOrder(order);
        }
        return cancelledTickets;
    }

    /**
     * Vé quá hạn thanh toán: huỷ vé, hoàn điểm/voucher đã tiêu lúc mua và báo
     * khách. Không có tiền nào đã vào ví nên không đụng tới settlement.
     */
    private int expireTicketOrder(PaymentOrder order) {
        com.fitmatch.entity.Ticket ticket = order.getTicket();
        if (ticket.getStatus() != com.fitmatch.common.enums.TicketStatus.PENDING_PAYMENT) {
            log.info("Payment order {} expired (ticket {} already {})",
                    order.getId(), ticket.getId(), ticket.getStatus());
            return 0;
        }
        ticketLifecycle.transition(ticket, com.fitmatch.common.enums.TicketStatus.CANCELLED,
                "Payment window expired");
        ticketPromotionReleaser.release(ticket);
        notificationDispatcher.ticketPaymentExpired(ticket);
        log.info("Payment order {} expired (ticket {} cancelled)", order.getId(), ticket.getId());
        return 1;
    }

    /** VietQR quick-link (img.vietqr.io) — FE render ảnh QR; addInfo = refCode để Casso đối soát. */
    private String buildVietQrUrl(String amount, String refCode) {
        PaymentProperties.Vietqr v = paymentProperties.getVietqr();
        String name = URLEncoder.encode(v.getAccountName() != null ? v.getAccountName() : "", StandardCharsets.UTF_8);
        return "https://img.vietqr.io/image/" + v.getBankBin() + "-" + v.getAccountNo() + "-"
                + v.getTemplate() + ".png?amount=" + amount + "&addInfo=" + refCode + "&accountName=" + name;
    }
}
