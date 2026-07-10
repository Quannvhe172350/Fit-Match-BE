package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.PaymentStatus;
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

    /** Trả về true nếu giao dịch khớp và thanh toán được xác nhận. */
    private boolean processItem(CassoWebhookRequest.Item item) {
        // Idempotency: bỏ qua giao dịch Casso đã xử lý (webhook có thể bắn trùng).
        if (paymentTransactionRepository.existsByExternalId(item.getId())) {
            log.info("Casso txn {} already processed - skipped", item.getId());
            return false;
        }
        String refCode = extractRef(item.getDescription());
        PaymentOrder order = refCode != null
                ? paymentOrderRepository.findByRefCode(refCode).orElse(null) : null;

        // Luôn lưu lại giao dịch (kể cả không khớp) để đối soát/kiểm toán.
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .externalId(item.getId())
                .amount(item.getAmount())
                .refCode(refCode)
                .rawDescription(item.getDescription())
                .paymentOrder(order)
                .build());

        if (order == null) {
            log.warn("Casso txn {} unmatched (ref {})", item.getId(), refCode);
            return false;
        }
        if (order.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment order {} not PENDING ({}) - txn recorded only", order.getId(), order.getStatus());
            return false;
        }
        // Đơn quá hạn không được xác nhận nữa; tiền đã vào cần đối soát hoàn thủ công.
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            order.setStatus(PaymentStatus.EXPIRED);
            paymentOrderRepository.save(order);
            log.warn("Casso txn {} arrived after order {} expired at {} - marked EXPIRED, needs manual reconciliation",
                    item.getId(), order.getId(), order.getExpiresAt());
            return false;
        }
        // Số tiền chuyển phải >= số phải trả; phần chuyển thừa cần đối soát thủ công.
        if (item.getAmount() == null || item.getAmount().compareTo(order.getAmount()) < 0) {
            log.warn("Casso txn {} amount {} < order {} amount {} - not confirmed",
                    item.getId(), item.getAmount(), order.getId(), order.getAmount());
            return false;
        }
        if (item.getAmount().compareTo(order.getAmount()) > 0) {
            log.warn("Casso txn {} overpaid: {} > order {} amount {} - confirmed with surplus, needs manual reconciliation",
                    item.getId(), item.getAmount(), order.getId(), order.getAmount());
        }

        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        if (order.getBooking().getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookingPaymentHandler.onPaymentConfirmed(order.getBooking(), order.getAmount(), "casso");
        }
        return true;
    }

    private String extractRef(String description) {
        if (description == null) {
            return null;
        }
        Matcher m = REF_PATTERN.matcher(description.toUpperCase());
        return m.find() ? m.group() : null;
    }
}
