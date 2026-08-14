package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.PaymentTxnDirection;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentTransactionResponse;
import com.fitmatch.dto.payment.ReconciliationSummaryResponse;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.PaymentTransaction;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.PaymentTransactionRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    /** Chỉ hai kết cục này được phép chốt bằng tay (APPLIED đi qua applyToTicket). */
    private static final Set<ReconStatus> ALLOWED_RESOLUTIONS =
            EnumSet.of(ReconStatus.RESOLVED_REFUNDED, ReconStatus.RESOLVED_IGNORED);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketRepository ticketRepository;
    private final com.fitmatch.service.support.TicketPaymentHandler ticketPaymentHandler;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionResponse> list(ReconStatus reconStatus,
                                                        PaymentTxnAnomaly anomaly,
                                                        Pageable pageable) {
        Page<PaymentTransaction> page;
        if (reconStatus != null && anomaly != null) {
            page = paymentTransactionRepository.findByReconStatusAndAnomaly(reconStatus, anomaly, pageable);
        } else if (reconStatus != null) {
            page = paymentTransactionRepository.findByReconStatus(reconStatus, pageable);
        } else if (anomaly != null) {
            page = paymentTransactionRepository.findByAnomaly(anomaly, pageable);
        } else {
            page = paymentTransactionRepository.findAll(pageable);
        }
        return PageResponse.of(page, PaymentTransactionResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationSummaryResponse summary() {
        List<Object[]> rows = paymentTransactionRepository.summarizeByAnomaly(ReconStatus.NEEDS_REVIEW);
        List<ReconciliationSummaryResponse.AnomalyBucket> buckets = rows.stream()
                .map(r -> ReconciliationSummaryResponse.AnomalyBucket.builder()
                        .anomaly((PaymentTxnAnomaly) r[0])
                        .count(((Number) r[1]).longValue())
                        .amount(r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO)
                        .build())
                .toList();
        return ReconciliationSummaryResponse.builder()
                .needsReviewCount(buckets.stream().mapToLong(
                        ReconciliationSummaryResponse.AnomalyBucket::getCount).sum())
                .needsReviewAmount(buckets.stream()
                        .map(ReconciliationSummaryResponse.AnomalyBucket::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .byAnomaly(buckets)
                .build();
    }

    @Override
    @Transactional
    public PaymentTransactionResponse applyToTicket(Long transactionId, Long ticketId,
                                                     boolean allowAmountMismatch,
                                                     String note, String actorUsername) {
        PaymentTransaction txn = requireOpenTransaction(transactionId);
        // V61: giao dịch CHI không bao giờ là tiền khách trả cho vé. Áp nó vào vé
        // sẽ hold ví một khoản tiền chưa từng vào tài khoản nền tảng.
        if (txn.getDirection() == PaymentTxnDirection.OUT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Giao dịch chi (OUT) không thể áp vào vé — đây là tiền nền tảng chuyển đi."
                            + " Hãy đối chiếu với lệnh rút tương ứng rồi chọn kết cục phù hợp.");
        }
        com.fitmatch.entity.Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Chỉ vé còn đang chờ tiền mới áp được: vé đã huỷ/đã thanh toán thì tiền
        // này phải trả lại người gửi, không được hold thêm lần nữa.
        if (ticket.getStatus() != com.fitmatch.common.enums.TicketStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé #" + ticketId + " không ở trạng thái chờ thanh toán (hiện tại: "
                            + ticket.getStatus() + "). Nếu khách đã chuyển tiền, hãy chọn"
                            + " 'đã chuyển trả người gửi'.");
        }
        PaymentOrder order = paymentOrderRepository.findByTicket_Id(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE,
                        "Vé #" + ticketId + " chưa có đơn thanh toán"));
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đơn thanh toán của vé #" + ticketId + " không còn chờ thanh toán (hiện tại: "
                            + order.getStatus() + ")");
        }
        if (!allowAmountMismatch
                && (txn.getAmount() == null || txn.getAmount().compareTo(order.getAmount()) < 0)) {
            throw new BusinessException(ErrorCode.PAYMENT_ERROR,
                    "Số tiền giao dịch (" + txn.getAmount() + ") nhỏ hơn số phải trả ("
                            + order.getAmount() + "). Bật allowAmountMismatch nếu khách chuyển"
                            + " nhiều lần và tổng đã đủ.");
        }
        if (allowAmountMismatch && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Bỏ qua kiểm tra số tiền thì phải ghi rõ lý do đối soát");
        }

        // Đi đúng luồng xác nhận thanh toán của mô hình vé: hold ví theo số tiền
        // thật đã nhận, vé -> ACTIVE, tích điểm, thông báo khách + gym.
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);
        ticketPaymentHandler.onPaymentConfirmed(ticket, order.getAmount(), "reconciliation");

        txn.setPaymentOrder(order);
        txn.setReconStatus(ReconStatus.RESOLVED_APPLIED);
        txn.setResolutionNote(note);
        txn.setResolvedBy(actorUsername);
        txn.setResolvedAt(LocalDateTime.now());
        paymentTransactionRepository.save(txn);

        auditService.record(AuditActions.PAYMENT_TXN_APPLY, "PaymentTransaction", transactionId,
                "Applied bank txn " + txn.getExternalId() + " (" + txn.getAmount() + ") to ticket #"
                        + ticketId + (allowAmountMismatch ? " [amount mismatch accepted]" : "")
                        + " by " + actorUsername);
        log.info("Reconciliation: txn {} applied to ticket {} by {}",
                transactionId, ticketId, actorUsername);
        return PaymentTransactionResponse.of(txn);
    }

    @Override
    @Transactional
    public PaymentTransactionResponse resolve(Long transactionId, ReconStatus resolution,
                                              String note, String actorUsername) {
        if (!ALLOWED_RESOLUTIONS.contains(resolution)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Kết cục không hợp lệ: " + resolution + " (chỉ RESOLVED_REFUNDED hoặc RESOLVED_IGNORED)");
        }
        PaymentTransaction txn = requireOpenTransaction(transactionId);
        txn.setReconStatus(resolution);
        txn.setResolutionNote(note);
        txn.setResolvedBy(actorUsername);
        txn.setResolvedAt(LocalDateTime.now());
        paymentTransactionRepository.save(txn);

        auditService.record(AuditActions.PAYMENT_TXN_RESOLVE, "PaymentTransaction", transactionId,
                resolution + " for bank txn " + txn.getExternalId() + " (" + txn.getAmount()
                        + ") by " + actorUsername + ": " + note);
        log.info("Reconciliation: txn {} resolved as {} by {}", transactionId, resolution, actorUsername);
        return PaymentTransactionResponse.of(txn);
    }

    private PaymentTransaction requireOpenTransaction(Long transactionId) {
        PaymentTransaction txn = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction", transactionId));
        if (txn.getReconStatus() != ReconStatus.NEEDS_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Giao dịch đã được đối soát trước đó (" + txn.getReconStatus() + ")");
        }
        return txn;
    }
}
