package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.CommissionConfig;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.Ticket;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.CommissionConfigService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final TicketRepository ticketRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletService walletService;
    private final CommissionConfigService commissionConfigService;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    @Override
    public void markTicketHeld(Ticket ticket) {
        ticket.setSettlementStatus(SettlementStatus.HELD);
    }

    @Override
    @Transactional
    public void settleTicketAfterFulfillment(Ticket ticket, String reason) {
        if (ticket.getSettlementStatus() != SettlementStatus.HELD) {
            // NONE (vé miễn phí do điểm/voucher phủ hết) hoặc đã xử lý — không có
            // gì để chuyển. Cũng là chốt chặn idempotent khi job chạy trùng.
            return;
        }
        BigDecimal heldAmount = resolveHeldAmount(ticket);
        if (heldAmount == null || heldAmount.compareTo(BigDecimal.ZERO) <= 0) {
            ticket.setSettlementStatus(SettlementStatus.NONE);
            return;
        }
        walletService.moveToPendingForTicket(ticket.getGymProfile().getId(), ticket.getId(), heldAmount);
        ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
        ticket.setSettlementAmount(heldAmount);
        ticket.setSettlementPendingAt(LocalDateTime.now());
        // Chốt % hoa hồng hiện hành để giải ngân về sau không bị áp hồi tố.
        ticket.setCommissionPercent(commissionConfigService.currentConfig().getCommissionPercent());
        auditService.record(AuditActions.SETTLEMENT_PENDING, "Ticket", ticket.getId(),
                "Moved " + heldAmount + " to pending settlement (" + reason + ")");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findTicketsDueForRelease() {
        int holdDays = commissionConfigService.currentConfig().getSettlementHoldDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(holdDays);
        return ticketRepository
                .findBySettlementStatusAndSettlementPendingAtBefore(SettlementStatus.PENDING_RELEASE, cutoff)
                .stream().map(Ticket::getId).toList();
    }

    @Override
    @Transactional
    public void releaseTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (ticket.getSettlementStatus() != SettlementStatus.PENDING_RELEASE) {
            return; // idempotent: job chạy trùng hoặc tranh chấp đã kéo tiền về held
        }
        BigDecimal commissionPercent = ticket.getCommissionPercent() != null
                ? ticket.getCommissionPercent()
                : commissionConfigService.currentConfig().getCommissionPercent();
        walletService.releaseForTicket(ticket.getGymProfile().getId(), ticket.getId(),
                ticket.getSettlementAmount(), commissionPercent);
        ticket.setSettlementStatus(SettlementStatus.RELEASED);
        ticketRepository.save(ticket);

        BigDecimal commission = ticket.getSettlementAmount()
                .multiply(commissionPercent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        notificationDispatcher.ticketSettlementReleased(ticket,
                ticket.getSettlementAmount().subtract(commission));
        auditService.record(AuditActions.SETTLEMENT_RELEASE, "Ticket", ticketId,
                "Released " + ticket.getSettlementAmount() + " to gym (commission "
                        + commissionPercent + "%)");
        log.info("Ticket {} settlement released ({}), commission {}%",
                ticketId, ticket.getSettlementAmount(), commissionPercent);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal heldAmountOfTicket(Ticket ticket) {
        return resolveHeldAmount(ticket);
    }

    private BigDecimal resolveHeldAmount(Ticket ticket) {
        return paymentOrderRepository.findByTicket_Id(ticket.getId())
                .filter(o -> o.getStatus() == PaymentStatus.PAID)
                .map(PaymentOrder::getAmount)
                .orElse(ticket.getPayableAmount());
    }
}
