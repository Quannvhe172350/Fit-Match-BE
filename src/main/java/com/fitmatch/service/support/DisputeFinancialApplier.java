package com.fitmatch.service.support;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.Ticket;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Áp dụng quyết định tranh chấp vào ví/thanh toán (UC-067). Tiền của booking
 * tranh chấp đã được kéo về held khi mở (settlement = DISPUTED), nên mọi kết
 * quả đều thao tác trên held: hoàn cho khách (refundFromHeld) và/hoặc chuyển
 * phần còn lại về Gym (moveToPending). Bảo toàn: refund + giữ lại = held.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeFinancialApplier {

    private final WalletService walletService;
    private final TicketRepository ticketRepository;
    private final NotificationDispatcher notificationDispatcher;

    public void apply(Dispute dispute, DisputeResolution resolution, BigDecimal refundAmount) {
        Ticket ticket = dispute.getTicket();
        BigDecimal held = dispute.getFrozenAmount() != null ? dispute.getFrozenAmount() : BigDecimal.ZERO;
        Long gymId = ticket.getGymProfile().getId();

        if (held.compareTo(BigDecimal.ZERO) <= 0) {
            boolean refundLike = resolution == DisputeResolution.REFUND_FULL
                    || resolution == DisputeResolution.REFUND_PARTIAL
                    || resolution == DisputeResolution.SPLIT
                    || resolution == DisputeResolution.PENALTY;
            if (ticket.getSettlementStatus() == SettlementStatus.RELEASED
                    || ticket.getSettlementStatus() == SettlementStatus.REFUNDED) {
                if (refundLike) {
                    throw new BusinessException(ErrorCode.INVALID_STATE,
                            "Tiền đã kết toán (" + ticket.getSettlementStatus()
                                    + "); quyết định này cần thu hồi thủ công — không tự hoàn được");
                }
                return;
            }
            ticket.setSettlementStatus(SettlementStatus.NONE);
            ticketRepository.save(ticket);
            return;
        }

        BigDecimal refund = switch (resolution) {
            case REFUND_FULL, PENALTY -> held;
            case RELEASE_TO_GYM, NO_ACTION -> BigDecimal.ZERO;
            case REFUND_PARTIAL, SPLIT -> {
                if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0
                        || refundAmount.compareTo(held) > 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "refundAmount phải > 0 và <= số tiền đang giữ " + held);
                }
                yield refundAmount;
            }
        };
        BigDecimal toGym = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            walletService.refundToCustomerForTicket(gymId, ticket.getCustomer(), ticket.getId(), refund);
            if (ticket.getCustomer() != null) {
                notificationDispatcher.refundCreditedToWallet(ticket.getCustomer(), refund, ticket.getId());
            }
        }
        if (toGym.compareTo(BigDecimal.ZERO) > 0) {
            walletService.moveToPendingForTicket(gymId, ticket.getId(), toGym);
            ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
            ticket.setSettlementAmount(toGym);
            ticket.setSettlementPendingAt(LocalDateTime.now());
        } else {
            ticket.setSettlementStatus(SettlementStatus.REFUNDED);
            ticket.setSettlementAmount(BigDecimal.ZERO);
        }
        ticketRepository.save(ticket);
        log.info("Dispute {} applied: resolution={}, refund={}, toGym={}, ticket={}, session={}",
                dispute.getId(), resolution, refund, toGym, ticket.getId(),
                dispute.getSession() != null ? dispute.getSession().getId() : null);
    }
}
