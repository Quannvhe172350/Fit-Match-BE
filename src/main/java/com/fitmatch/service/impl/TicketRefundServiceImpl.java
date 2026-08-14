package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.RefundMode;
import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.ticket.ApproveTicketRefundRequest;
import com.fitmatch.dto.ticket.TicketRefundPreviewResponse;
import com.fitmatch.entity.RefundRequest;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.RefundRequestRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.TicketRefundService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PartialRefundCalculator;
import com.fitmatch.service.support.PartialRefundCalculator.RefundSplit;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.TicketLifecycle;
import com.fitmatch.service.support.TicketPromotionReleaser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketRefundServiceImpl implements TicketRefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final TicketRepository ticketRepository;
    private final TrainingSessionRepository sessionRepository;
    private final PartialRefundCalculator refundCalculator;
    private final WalletService walletService;
    private final SettlementService settlementService;
    private final TicketLifecycle ticketLifecycle;
    private final SessionLifecycle sessionLifecycle;
    private final TicketPromotionReleaser promotionReleaser;
    private final AuditService auditService;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public RefundResponse requestByCustomer(String customerUsername, Long ticketId, String reason) {
        Ticket ticket = ticketRepository.findByIdAndCustomer_Username(ticketId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Câu 32: hết hạn là tiền đã về gym — nói thẳng lý do thay vì để khách
        // đoán, vì đây là câu hỏi hỗ trợ chắc chắn sẽ phát sinh.
        if (ticket.getStatus() == TicketStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé đã hết hạn ngày " + ticket.getExpiresAt().toLocalDate()
                            + " nên không hoàn tiền được nữa. Bạn có thể mở tranh chấp nếu cho rằng có sai sót.");
        }
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ vé đang sử dụng được mới yêu cầu hoàn tiền (hiện: " + ticket.getStatus() + ")");
        }
        if (ticket.getSettlementStatus() != SettlementStatus.HELD) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé này không có khoản tiền nào đang được giữ (settlement: "
                            + ticket.getSettlementStatus() + ")");
        }
        if (refundRequestRepository.existsByTicket_IdAndStatus(ticketId, RefundStatus.PENDING)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé này đã có một yêu cầu hoàn tiền đang chờ duyệt");
        }

        BigDecimal held = settlementService.heldAmountOfTicket(ticket);
        if (held == null || held.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Vé này không có số tiền nào để hoàn");
        }

        RefundRequest request = refundRequestRepository.save(RefundRequest.builder()
                .ticket(ticket)
                .amount(held)
                .reason(reason)
                .status(RefundStatus.PENDING)
                .build());
        // Chặn auto-release trong lúc chờ admin quyết.
        ticket.setSettlementStatus(SettlementStatus.REFUND_PENDING);
        ticketRepository.save(ticket);

        auditService.record(AuditActions.REFUND_REQUEST_CREATE, "RefundRequest", request.getId(),
                "Refund " + held + " requested for ticket " + ticketId + ": " + reason);
        log.info("Refund request {} opened for ticket {} ({})", request.getId(), ticketId, held);
        return RefundResponse.of(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> listForCustomer(String customerUsername, Pageable pageable) {
        return PageResponse.of(
                refundRequestRepository.findByTicket_Customer_Username(customerUsername, pageable),
                RefundResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> listForAdmin(RefundStatus status, Pageable pageable) {
        Page<RefundRequest> page = status == null
                ? refundRequestRepository.findAll(pageable)
                : refundRequestRepository.findByStatus(status, pageable);
        return PageResponse.of(page, RefundResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketRefundPreviewResponse preview(Long refundRequestId) {
        RefundRequest request = requireTicketRequest(refundRequestId);
        Ticket ticket = request.getTicket();
        LocalDate today = LocalDate.now();

        RefundSplit partial = refundCalculator.partialElapsed(ticket, today);
        int futureSessions = futureSessions(ticket).size();

        return TicketRefundPreviewResponse.builder()
                .refundRequestId(request.getId())
                .ticketId(ticket.getId())
                .ticketTypeName(ticket.getTicketType().getName())
                .customerName(ticket.getCustomer().getFullName())
                .paidAmount(ticket.getPayableAmount())
                .dayCount(ticket.getDayCount())
                .startDate(ticket.getStartDate())
                .elapsedDays(partial.elapsedDays())
                .fullRefund(ticket.getPayableAmount())
                .partialRefund(partial.refund())
                .retained(partial.retained())
                // Câu 13: chưa dùng ngày nào -> hai lựa chọn cho ra cùng số tiền,
                // FE chỉ hiện một nút thay vì bắt admin chọn giữa hai thứ giống nhau.
                .fullRefundOnly(partial.elapsedDays() == 0)
                .futureSessionsToCancel(futureSessions)
                .build();
    }

    @Override
    @Transactional
    public RefundResponse approve(Long refundRequestId, ApproveTicketRefundRequest req,
                                  String actorUsername) {
        RefundRequest request = requirePending(refundRequestId);
        Ticket ticket = request.getTicket();

        // Chặn double-spend với luồng tranh chấp: mở tranh chấp kéo vé sang
        // DISPUTED và tiền do DisputeFinancialApplier xử lý. Nếu settlement không
        // còn REFUND_PENDING thì không được trừ held lần nữa.
        if (ticket.getSettlementStatus() != SettlementStatus.REFUND_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể thực thi hoàn tiền: settlement của vé đang là "
                            + ticket.getSettlementStatus() + " (cần REFUND_PENDING; vé có thể đang bị tranh chấp)");
        }

        RefundMode mode = req.getMode();
        RefundSplit split = mode == RefundMode.FULL
                ? refundCalculator.full(ticket)
                : refundCalculator.partialElapsed(ticket);

        BigDecimal refund = split.refund();
        BigDecimal retained = split.retained();
        Long gymId = ticket.getGymProfile().getId();

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            walletService.refundToCustomerForTicket(gymId, ticket.getCustomer(), ticket.getId(), refund);
        }
        if (retained.compareTo(BigDecimal.ZERO) > 0) {
            // Phần tương ứng số ngày đã tập thuộc về gym — vào pending settlement
            // và đi theo chu kỳ giải ngân bình thường.
            walletService.moveToPendingForTicket(gymId, ticket.getId(), retained);
            ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
            ticket.setSettlementAmount(retained);
            ticket.setSettlementPendingAt(LocalDateTime.now());
            ticket.setCommissionPercent(null);
        } else {
            ticket.setSettlementStatus(SettlementStatus.REFUNDED);
        }

        // Câu 12: duyệt hoàn là huỷ sạch buổi tập tương lai — giữ lại thì khách
        // vừa được hoàn tiền vừa còn chỗ, và lịch của PT bị treo vô nghĩa.
        int cancelled = cancelFutureSessions(ticket);

        ticketLifecycle.transition(ticket, TicketStatus.REFUNDED,
                "Refund " + mode + " approved by " + actorUsername);

        // Chỉ hoàn điểm/voucher khi hoàn TOÀN BỘ: hoàn một phần nghĩa là dịch vụ
        // đã được cung cấp một phần, trả lại điểm nữa là bồi thường hai lần.
        if (mode == RefundMode.FULL) {
            promotionReleaser.release(ticket);
        }
        ticketRepository.save(ticket);

        request.setStatus(RefundStatus.EXECUTED);
        request.setRefundMode(mode);
        request.setElapsedDays(split.elapsedDays());
        request.setRetainedAmount(retained);
        request.setDecisionNote("Refunded " + refund
                + (retained.compareTo(BigDecimal.ZERO) > 0 ? ", gym retained " + retained : "")
                + " (" + mode + ", " + split.elapsedDays() + "/" + ticket.getDayCount() + " ngày đã qua)"
                + (req.getNote() != null && !req.getNote().isBlank() ? " — " + req.getNote() : ""));
        refundRequestRepository.save(request);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            notificationDispatcher.ticketRefundExecuted(ticket, refund, cancelled);
        }
        auditService.record(AuditActions.REFUND_EXECUTE, "RefundRequest", refundRequestId,
                "Refund " + refund + " (" + mode + ", retained " + retained + ") executed by "
                        + actorUsername + " for ticket " + ticket.getId()
                        + "; cancelled " + cancelled + " future session(s)");
        log.info("Refund {} executed on ticket {}: mode={}, refund={}, retained={}, cancelled={}",
                refundRequestId, ticket.getId(), mode, refund, retained, cancelled);
        return RefundResponse.of(request);
    }

    @Override
    @Transactional
    public RefundResponse reject(Long refundRequestId, String note, String actorUsername) {
        RefundRequest request = requirePending(refundRequestId);
        Ticket ticket = request.getTicket();

        request.setStatus(RefundStatus.REJECTED);
        request.setDecisionNote(note);
        refundRequestRepository.save(request);

        // Tiền quay lại HELD để vé tiếp tục dùng được và chu kỳ giải ngân chạy tiếp.
        if (ticket.getSettlementStatus() == SettlementStatus.REFUND_PENDING) {
            ticket.setSettlementStatus(SettlementStatus.HELD);
            ticketRepository.save(ticket);
        }
        notificationDispatcher.ticketRefundRejected(ticket, request.getAmount(), note);
        auditService.record(AuditActions.REFUND_REJECT, "RefundRequest", refundRequestId,
                "Refund rejected by " + actorUsername + ": " + note);
        return RefundResponse.of(request);
    }

    // ---------- helpers ----------

    /** Buổi còn SCHEDULED từ hôm nay trở đi — buổi hôm nay cũng tính là tương lai. */
    private List<TrainingSession> futureSessions(Ticket ticket) {
        return sessionRepository.findByTicket_IdAndStatusAndSessionDateGreaterThanEqual(
                ticket.getId(), SessionStatus.SCHEDULED, LocalDate.now());
    }

    private int cancelFutureSessions(Ticket ticket) {
        List<TrainingSession> sessions = futureSessions(ticket);
        for (TrainingSession session : sessions) {
            sessionLifecycle.transition(session, SessionStatus.CANCELLED, "Vé được hoàn tiền");
            sessionRepository.save(session);
        }
        return sessions.size();
    }

    private RefundRequest requireTicketRequest(Long refundRequestId) {
        RefundRequest request = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request", refundRequestId));
        if (request.getTicket() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Yêu cầu hoàn này thuộc luồng booking cũ, không dùng được ở đây");
        }
        return request;
    }

    private RefundRequest requirePending(Long refundRequestId) {
        RefundRequest request = requireTicketRequest(refundRequestId);
        if (request.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Yêu cầu hoàn không còn ở trạng thái chờ duyệt (hiện: " + request.getStatus() + ")");
        }
        return request;
    }
}
