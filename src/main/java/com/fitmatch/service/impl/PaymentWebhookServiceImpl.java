package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.dto.payment.CassoWebhookRequest;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.PaymentTransaction;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.PaymentTransactionRepository;
import com.fitmatch.service.PaymentWebhookService;
import com.fitmatch.service.support.BookingPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    /** refCode dạng FM<digits><6 hex uppercase>, dò trong nội dung chuyển khoản. */
    private static final Pattern REF_PATTERN = Pattern.compile("FM\\d+[0-9A-F]{6}");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final BookingPaymentHandler bookingPaymentHandler;
    private final TransactionTemplate transactionTemplate;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    @Override
    public int processCasso(CassoWebhookRequest request) {
        if (request.getData() == null || request.getData().isEmpty()) {
            return 0;
        }
        int matched = 0;
        for (CassoWebhookRequest.Item item : request.getData()) {
            if (item.getId() == null) {
                continue;
            }
            // Mỗi giao dịch một transaction riêng: một item lỗi không rollback
            // các item đã khớp; item lỗi chưa lưu PaymentTransaction nên lần
            // webhook retry sau vẫn xử lý lại được (idempotency theo external_id).
            try {
                Boolean confirmed = transactionTemplate.execute(status -> processItem(item));
                if (Boolean.TRUE.equals(confirmed)) {
                    matched++;
                }
            } catch (Exception e) {
                log.error("Casso txn {} failed - will retry on next webhook", item.getId(), e);
            }
        }
        log.info("Casso webhook processed: {} matched of {}", matched, request.getData().size());
        return matched;
    }

    /**
     * Trả về true nếu giao dịch khớp và thanh toán được xác nhận.
     * <p>
     * Mọi giao dịch đều được lưu kèm kết quả phân loại đối soát: khớp trọn vẹn ->
     * {@link ReconStatus#APPLIED}; còn lại -> {@link ReconStatus#NEEDS_REVIEW} kèm
     * {@link PaymentTxnAnomaly} để Finance xử lý ở màn đối soát (UC-053/056).
     * Trước đây các ca bất thường chỉ ghi log nên tiền thật vào tài khoản mà
     * không ai lần ra được.
     */
    private boolean processItem(CassoWebhookRequest.Item item) {
        // Idempotency: bỏ qua giao dịch Casso đã xử lý (webhook có thể bắn trùng).
        if (paymentTransactionRepository.existsByExternalId(item.getId())) {
            log.info("Casso txn {} already processed - skipped", item.getId());
            return false;
        }
        String refCode = extractRef(item.getDescription());
        PaymentOrder order = refCode != null
                ? paymentOrderRepository.findByRefCode(refCode).orElse(null) : null;

        PaymentTxnAnomaly anomaly = classify(item, order);
        boolean confirm = anomaly == null || anomaly == PaymentTxnAnomaly.OVERPAID;

        // Luôn lưu lại giao dịch (kể cả không khớp) để đối soát/kiểm toán.
        // OVERPAID vẫn phải review: booking đã xác nhận nhưng phần thừa còn nợ khách.
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .externalId(item.getId())
                .amount(item.getAmount())
                .refCode(refCode)
                .rawDescription(item.getDescription())
                .paymentOrder(order)
                .anomaly(anomaly)
                .reconStatus(anomaly == null ? ReconStatus.APPLIED : ReconStatus.NEEDS_REVIEW)
                .build());

        if (anomaly != null) {
            log.warn("Casso txn {} flagged {} (ref {}, amount {}, order {}) - queued for manual reconciliation",
                    item.getId(), anomaly, refCode, item.getAmount(),
                    order != null ? order.getId() : null);
        }
        if (!confirm) {
            handleUnconfirmed(item, order, anomaly);
            return false;
        }

        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        if (order.getBooking().getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookingPaymentHandler.onPaymentConfirmed(order.getBooking(), order.getAmount(), "casso");
        }
        return true;
    }

    /** Null = khớp trọn vẹn, xác nhận được ngay. */
    private PaymentTxnAnomaly classify(CassoWebhookRequest.Item item, PaymentOrder order) {
        if (order == null) {
            return PaymentTxnAnomaly.UNMATCHED;
        }
        if (order.getStatus() != PaymentStatus.PENDING) {
            return PaymentTxnAnomaly.DUPLICATE;
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            return PaymentTxnAnomaly.LATE_ARRIVAL;
        }
        if (item.getAmount() == null || item.getAmount().compareTo(order.getAmount()) < 0) {
            return PaymentTxnAnomaly.UNDERPAID;
        }
        if (item.getAmount().compareTo(order.getAmount()) > 0) {
            return PaymentTxnAnomaly.OVERPAID;
        }
        return null;
    }

    /** Đóng đơn quá hạn và báo khách (Bug 9) cho các ca không xác nhận được. */
    private void handleUnconfirmed(CassoWebhookRequest.Item item, PaymentOrder order,
                                   PaymentTxnAnomaly anomaly) {
        if (anomaly == PaymentTxnAnomaly.LATE_ARRIVAL) {
            // Đơn quá hạn không được xác nhận nữa; tiền đã vào cần hoàn thủ công.
            order.setStatus(PaymentStatus.EXPIRED);
            paymentOrderRepository.save(order);
            notificationDispatcher.paymentFailed(order.getBooking(),
                    "tiền vào sau khi đơn thanh toán đã hết hạn");
        } else if (anomaly == PaymentTxnAnomaly.UNDERPAID) {
            notificationDispatcher.paymentFailed(order.getBooking(),
                    "số tiền chuyển chưa đủ so với số phải trả");
        }
        // UNMATCHED: không biết booking nào -> không thể báo ai, chỉ vào hàng đợi.
        // DUPLICATE: đơn đã xử lý xong -> không làm khách hoang mang, Finance đối soát.
    }

    private String extractRef(String description) {
        if (description == null) {
            return null;
        }
        Matcher m = REF_PATTERN.matcher(description.toUpperCase());
        return m.find() ? m.group() : null;
    }
}
