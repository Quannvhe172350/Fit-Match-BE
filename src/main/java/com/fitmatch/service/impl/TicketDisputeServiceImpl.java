package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.TicketDisputeService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.DisputeWindow;
import com.fitmatch.service.support.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketDisputeServiceImpl implements TicketDisputeService {

    /**
     * Vé mở tranh chấp được khi đang dùng hoặc đã dùng hết. Vé chưa thanh toán
     * chưa có tiền để tranh chấp; vé đã hoàn/huỷ thì đã có kết luận rồi.
     * EXPIRED vẫn cho mở — câu 32 chặn HOÀN TIỀN tự động, nhưng nếu gym thực sự
     * sai thì tranh chấp là đường duy nhất còn lại của khách.
     */
    private static final Set<TicketStatus> DISPUTABLE_TICKET_STATES =
            Set.of(TicketStatus.ACTIVE, TicketStatus.USED_UP, TicketStatus.EXPIRED);

    private static final List<DisputeStatus> OPEN_STATES = List.of(
            DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW, DisputeStatus.ESCALATED);

    private final DisputeRepository disputeRepository;
    private final TicketRepository ticketRepository;
    private final TrainingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final NotificationDispatcher notificationDispatcher;
    private final DisputeWindow disputeWindow;

    @Override
    @Transactional
    public DisputeResponse open(String username, Long ticketId, Long sessionId, String reason) {
        Ticket ticket = requireParty(username, ticketId);
        if (!DISPUTABLE_TICKET_STATES.contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không mở được tranh chấp cho vé ở trạng thái " + ticket.getStatus());
        }
        // D-18: hết cửa sổ khiếu nại thì chặn ngay tại đây. Trước đây vé quá hạn
        // vẫn mở được tranh chấp nhưng tiền đã giải ngân xong nên đóng băng được
        // 0đ — tới lúc moderator quyết hoàn tiền mới báo "cần thu hồi thủ công".
        // Chặn sớm và nói rõ ngày hết hạn tử tế hơn nhiều so với cái bẫy đó.
        if (disputeWindow.expired(ticket)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đã hết hạn mở tranh chấp cho vé này (hạn cuối "
                            + disputeWindow.deadline(ticket) + ")");
        }

        TrainingSession session = null;
        if (sessionId != null) {
            session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Training session", sessionId));
            if (!session.getTicket().getId().equals(ticketId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Buổi tập này không thuộc vé đã chọn");
            }
            // Tranh chấp cấp buổi chỉ có nghĩa sau khi buổi đã diễn ra — trước đó
            // chưa có gì để khiếu nại, và khách vẫn còn quyền đổi lịch/đổi PT.
            if (session.getStatus() != SessionStatus.DONE) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Chỉ mở tranh chấp cho buổi tập đã diễn ra (hiện: " + session.getStatus() + ")");
            }
        }

        // Khoá vé trước khi kiểm tra trùng — chống hai luồng cùng mở tranh chấp
        // rồi cùng đóng băng quỹ (giữ nguyên cách làm P0-0.5 của mô hình cũ).
        ticketRepository.lockById(ticketId);
        if (disputeRepository.existsByTicket_IdAndStatusIn(ticketId, OPEN_STATES)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé này đang có một tranh chấp chưa được giải quyết");
        }

        BigDecimal frozen = protectFunds(ticket, session);

        User opener = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .ticket(ticket)
                .session(session)
                .openedByRole(opener.getRole().name())
                .reason(reason)
                .status(DisputeStatus.OPEN)
                .frozenAmount(frozen)
                .build());

        notificationDispatcher.disputeOpened(dispute, username);
        auditService.record(AuditActions.DISPUTE_OPEN, "Dispute", dispute.getId(),
                "Opened by " + username + " (" + opener.getRole() + ") for ticket " + ticketId
                        + (sessionId != null ? ", session " + sessionId : "")
                        + " (frozen " + frozen + ")");
        log.info("Dispute {} opened for ticket {} session {} (frozen {})",
                dispute.getId(), ticketId, sessionId, frozen);
        return DisputeResponse.of(dispute);
    }

    /**
     * Kéo tiền về held và đánh dấu DISPUTED để scheduler không auto-release.
     *
     * <p>Số tiền đóng băng khác nhau theo cấp: tranh chấp cấp vé bảo vệ toàn bộ
     * phần đang giữ; tranh chấp cấp buổi chỉ bảo vệ giá trị MỘT ngày
     * ({@code payableAmount / dayCount}) — phần còn lại của vé không bị treo
     * theo, vì các buổi khác vẫn diễn ra bình thường.
     *
     * <p>Với vé đang PENDING_RELEASE, phần KHÔNG bị đóng băng vẫn nằm ở
     * {@code pendingBalance} của ví gym, nên {@code settlementAmount} được hạ
     * xuống đúng phần đó — nó là con số {@code SettlementReleaseJob} sẽ giải ngân
     * và là con số {@link com.fitmatch.service.support.DisputeFinancialApplier}
     * cộng lại khi tranh chấp có kết luận. Không hạ thì phần dư mất chỗ neo:
     * tranh chấp một buổi của vé 10 ngày sẽ khoá luôn 9 ngày tiền còn lại.
     */
    private BigDecimal protectFunds(Ticket ticket, TrainingSession session) {
        SettlementStatus status = ticket.getSettlementStatus();
        BigDecimal available = switch (status) {
            case PENDING_RELEASE -> ticket.getSettlementAmount() != null
                    ? ticket.getSettlementAmount() : BigDecimal.ZERO;
            case HELD, REFUND_PENDING -> settlementService.heldAmountOfTicket(ticket);
            default -> BigDecimal.ZERO; // NONE/RELEASED/REFUNDED/DISPUTED: không còn gì để bảo vệ
        };
        if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = session == null ? available : perDayValue(ticket).min(available);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (status == SettlementStatus.PENDING_RELEASE) {
            walletService.reverseToHeldForTicket(ticket.getGymProfile().getId(), ticket.getId(), amount);
            ticket.setSettlementAmount(available.subtract(amount));
        } else {
            // Đóng băng từ held (HELD/REFUND_PENDING): không có đồng nào ở pending.
            // Ghi 0 thay vì để số cũ nằm lại — applier cộng settlementAmount vào
            // phần trả gym, một giá trị sót từ chu kỳ trước sẽ thành tiền khống.
            ticket.setSettlementAmount(BigDecimal.ZERO);
        }
        ticket.setSettlementStatus(SettlementStatus.DISPUTED);
        ticketRepository.save(ticket);
        return amount;
    }

    /** Giá trị một ngày tập — cùng công thức perDay của PartialRefundCalculator. */
    private BigDecimal perDayValue(Ticket ticket) {
        BigDecimal payable = ticket.getPayableAmount() != null
                ? ticket.getPayableAmount() : BigDecimal.ZERO;
        int dayCount = ticket.getDayCount() != null && ticket.getDayCount() > 0
                ? ticket.getDayCount() : 1;
        return payable.divide(BigDecimal.valueOf(dayCount), 0, RoundingMode.HALF_UP);
    }

    /** Khách của vé, chủ gym, hoặc PT có buổi thuộc vé này đều được mở tranh chấp. */
    private Ticket requireParty(String username, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        boolean isCustomer = ticket.getCustomer().getUsername().equals(username);
        boolean isGym = ticket.getGymProfile().getUser() != null
                && ticket.getGymProfile().getUser().getUsername().equals(username);
        boolean isPt = sessionRepository.findByTicket_IdOrderByDayIndexAsc(ticketId).stream()
                .anyMatch(s -> s.getPtProfile() != null
                        && s.getPtProfile().getUser() != null
                        && s.getPtProfile().getUser().getUsername().equals(username));
        if (!isCustomer && !isGym && !isPt) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Bạn không phải một bên liên quan của vé này");
        }
        return ticket;
    }
}
