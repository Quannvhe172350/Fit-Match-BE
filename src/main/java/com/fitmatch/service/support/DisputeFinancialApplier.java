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
            // Chỉ dọn trạng thái của vé mà chính tranh chấp này đã treo lên. Vé
            // đang PENDING_RELEASE/HELD mà tranh chấp không giữ đồng nào thì để
            // yên — đặt NONE ở đây sẽ xoá mất chỗ neo của tiền đang chờ.
            if (ticket.getSettlementStatus() == SettlementStatus.DISPUTED) {
                ticket.setSettlementStatus(SettlementStatus.NONE);
                ticketRepository.save(ticket);
            }
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
        }
        // Tranh chấp cấp buổi chỉ đóng băng giá trị một ngày; phần còn lại của vé
        // vẫn nằm ở pending và settlementAmount đang giữ đúng con số đó (xem
        // TicketDisputeServiceImpl#protectFunds). Cộng vào chứ không ghi đè —
        // ghi đè là bỏ rơi phần dư: vé rời PENDING_RELEASE nên job giải ngân
        // không bao giờ quét tới, tiền kẹt ở pending vĩnh viễn.
        BigDecimal stillPending = ticket.getSettlementAmount() != null
                ? ticket.getSettlementAmount() : BigDecimal.ZERO;
        BigDecimal pendingTotal = stillPending.add(toGym);
        if (pendingTotal.compareTo(BigDecimal.ZERO) > 0) {
            ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
            ticket.setSettlementAmount(pendingTotal);
            // Giữ mốc cũ nếu vé đã từng chờ giải ngân: nó là điểm neo của CẢ hạn
            // giữ tiền lẫn hạn khiếu nại (DisputeWindow), và gym không đáng bị
            // dời hạn thêm một chu kỳ vì một tranh chấp đã có kết luận.
            if (ticket.getSettlementPendingAt() == null) {
                ticket.setSettlementPendingAt(LocalDateTime.now());
            }
        } else {
            ticket.setSettlementStatus(SettlementStatus.REFUNDED);
            ticket.setSettlementAmount(BigDecimal.ZERO);
        }
        ticketRepository.save(ticket);
        log.info("Dispute {} applied: resolution={}, refund={}, toGym={}, stillPending={}, ticket={}, session={}",
                dispute.getId(), resolution, refund, toGym, stillPending, ticket.getId(),
                dispute.getSession() != null ? dispute.getSession().getId() : null);
    }
}
