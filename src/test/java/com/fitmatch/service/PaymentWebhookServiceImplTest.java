package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.dto.payment.CassoWebhookRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.PaymentTransaction;
import com.fitmatch.common.enums.PaymentTxnDirection;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.WithdrawalRequest;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.PaymentTransactionRepository;
import com.fitmatch.repository.WithdrawalRequestRepository;
import com.fitmatch.service.impl.PaymentWebhookServiceImpl;
import com.fitmatch.service.support.BookingPaymentHandler;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phủ điểm vào tiền quan trọng nhất (UC-053) mà audit ghi nhận đang 0 test:
 * idempotency theo external_id, không khớp, thiếu tiền, đơn hết hạn/không PENDING,
 * và happy path xác nhận + hold.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private BookingPaymentHandler bookingPaymentHandler;
    @Mock private WithdrawalService withdrawalService;
    @Mock private TransactionTemplate transactionTemplate;
    // Thiếu mock này thì paymentFailed(...) ném NPE, bị per-item try/catch nuốt —
    // các case thiếu tiền/hết hạn trước đây "pass" mà không thực sự chạy hết luồng.
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private PaymentWebhookServiceImpl service;

    @Captor private ArgumentCaptor<PaymentTransaction> txnCaptor;

    @BeforeEach
    void runCallbackInline() {
        // Chạy callback của transactionTemplate ngay (không cần tx thật).
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private CassoWebhookRequest oneItem(String id, BigDecimal amount, String desc) {
        return CassoWebhookRequest.builder()
                .data(List.of(CassoWebhookRequest.Item.builder()
                        .id(id).amount(amount).description(desc).build()))
                .build();
    }

    private PaymentOrder order(PaymentStatus status, BigDecimal amount,
                               LocalDateTime expiresAt, BookingStatus bookingStatus) {
        return PaymentOrder.builder()
                .id(5L).refCode("FM10ABCDEF").amount(amount).status(status).expiresAt(expiresAt)
                .booking(Booking.builder().id(1L).status(bookingStatus).build())
                .build();
    }

    @Test
    void alreadyProcessed_idempotentSkip() {
        when(paymentTransactionRepository.existsByExternalId("casso-1")).thenReturn(true);

        int matched = service.processCasso(oneItem("casso-1", new BigDecimal("100000"), "FM10ABCDEF"));

        assertThat(matched).isZero();
        verify(paymentOrderRepository, never()).findByRefCode(anyString());
        verify(paymentTransactionRepository, never()).save(any());
    }

    /** Giao dịch đã lưu — dùng để soi kết quả phân loại đối soát (UC-053). */
    private PaymentTransaction savedTxn() {
        verify(paymentTransactionRepository).save(txnCaptor.capture());
        return txnCaptor.getValue();
    }

    @Test
    void unmatchedRef_recordedButNotConfirmed() {
        when(paymentTransactionRepository.existsByExternalId("casso-2")).thenReturn(false);

        int matched = service.processCasso(oneItem("casso-2", new BigDecimal("100000"), "no ref here"));

        assertThat(matched).isZero();
        verify(bookingPaymentHandler, never()).onPaymentConfirmed(any(), any(), anyString());
        // Tiền vào không khớp phải vào hàng đợi đối soát, không được chỉ nằm ở log.
        PaymentTransaction txn = savedTxn();
        assertThat(txn.getAnomaly()).isEqualTo(PaymentTxnAnomaly.UNMATCHED);
        assertThat(txn.getReconStatus()).isEqualTo(ReconStatus.NEEDS_REVIEW);
        assertThat(txn.getPaymentOrder()).isNull();
    }

    @Test
    void amountShort_notConfirmedAndQueued() {
        when(paymentTransactionRepository.existsByExternalId("casso-3")).thenReturn(false);
        PaymentOrder o = order(PaymentStatus.PENDING, new BigDecimal("100000"),
                LocalDateTime.now().plusHours(1), BookingStatus.PENDING_PAYMENT);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.of(o));

        int matched = service.processCasso(oneItem("casso-3", new BigDecimal("50000"), "FM10ABCDEF"));

        assertThat(matched).isZero();
        assertThat(o.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(bookingPaymentHandler, never()).onPaymentConfirmed(any(), any(), anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.UNDERPAID);
        verify(notificationDispatcher).paymentFailed(eq(o.getBooking()), anyString());
    }

    @Test
    void orderExpired_markedExpiredNotConfirmed() {
        when(paymentTransactionRepository.existsByExternalId("casso-4")).thenReturn(false);
        PaymentOrder o = order(PaymentStatus.PENDING, new BigDecimal("100000"),
                LocalDateTime.now().minusMinutes(1), BookingStatus.PENDING_PAYMENT);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.of(o));

        int matched = service.processCasso(oneItem("casso-4", new BigDecimal("100000"), "FM10ABCDEF"));

        assertThat(matched).isZero();
        assertThat(o.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(bookingPaymentHandler, never()).onPaymentConfirmed(any(), any(), anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.LATE_ARRIVAL);
        verify(notificationDispatcher).paymentFailed(eq(o.getBooking()), anyString());
    }

    @Test
    void orderNotPending_queuedAsDuplicateWithoutNotifying() {
        when(paymentTransactionRepository.existsByExternalId("casso-5")).thenReturn(false);
        PaymentOrder o = order(PaymentStatus.PAID, new BigDecimal("100000"),
                LocalDateTime.now().plusHours(1), BookingStatus.CONFIRMED);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.of(o));

        int matched = service.processCasso(oneItem("casso-5", new BigDecimal("100000"), "FM10ABCDEF"));

        assertThat(matched).isZero();
        verify(bookingPaymentHandler, never()).onPaymentConfirmed(any(), any(), anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.DUPLICATE);
        // Booking của khách đã xong — không làm khách hoang mang, để Finance đối soát.
        verify(notificationDispatcher, never()).paymentFailed(any(), anyString());
    }

    @Test
    void happyPath_confirmsAndHoldsFunds() {
        when(paymentTransactionRepository.existsByExternalId("casso-6")).thenReturn(false);
        PaymentOrder o = order(PaymentStatus.PENDING, new BigDecimal("100000"),
                LocalDateTime.now().plusHours(1), BookingStatus.PENDING_PAYMENT);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.of(o));

        int matched = service.processCasso(oneItem("casso-6", new BigDecimal("100000"), "FM10ABCDEF"));

        assertThat(matched).isEqualTo(1);
        assertThat(o.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(o.getPaidAt()).isNotNull();
        verify(bookingPaymentHandler).onPaymentConfirmed(eq(o.getBooking()),
                eq(new BigDecimal("100000")), eq("casso"));
        PaymentTransaction txn = savedTxn();
        assertThat(txn.getAnomaly()).isNull();
        assertThat(txn.getReconStatus()).isEqualTo(ReconStatus.APPLIED);
    }

    @Test
    void overpaid_confirmsBookingButStillNeedsReview() {
        when(paymentTransactionRepository.existsByExternalId("casso-7")).thenReturn(false);
        PaymentOrder o = order(PaymentStatus.PENDING, new BigDecimal("100000"),
                LocalDateTime.now().plusHours(1), BookingStatus.PENDING_PAYMENT);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.of(o));

        int matched = service.processCasso(oneItem("casso-7", new BigDecimal("150000"), "FM10ABCDEF"));

        // Khách đã trả đủ (và thừa) -> booking phải đi tiếp, nhưng phần thừa còn nợ khách.
        assertThat(matched).isEqualTo(1);
        assertThat(o.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(bookingPaymentHandler).onPaymentConfirmed(eq(o.getBooking()),
                eq(new BigDecimal("100000")), eq("casso"));
        PaymentTransaction txn = savedTxn();
        assertThat(txn.getAnomaly()).isEqualTo(PaymentTxnAnomaly.OVERPAID);
        assertThat(txn.getReconStatus()).isEqualTo(ReconStatus.NEEDS_REVIEW);
    }

    // ----- V61: chiều CHI — xác nhận lệnh rút bằng biến động số dư -----

    private WithdrawalRequest withdrawal(WithdrawalStatus status, String amount) {
        return WithdrawalRequest.builder()
                .id(77L).refCode("FMW77ABC123").amount(new BigDecimal(amount)).status(status)
                .build();
    }

    @Test
    void outgoingMatchingApprovedWithdrawal_autoMarksPaid() {
        when(paymentTransactionRepository.existsByExternalId("casso-8")).thenReturn(false);
        WithdrawalRequest wr = withdrawal(WithdrawalStatus.APPROVED, "500000");
        when(withdrawalRequestRepository.findByRefCode("FMW77ABC123")).thenReturn(Optional.of(wr));

        // Casso gửi số tiền ÂM cho giao dịch ghi nợ.
        int matched = service.processCasso(
                oneItem("casso-8", new BigDecimal("-500000"), "CK FMW77ABC123"));

        assertThat(matched).isEqualTo(1);
        verify(withdrawalService).markPaidByReconciliation(77L, "casso-8");
        PaymentTransaction txn = savedTxn();
        assertThat(txn.getDirection()).isEqualTo(PaymentTxnDirection.OUT);
        assertThat(txn.getAnomaly()).isNull();
        assertThat(txn.getReconStatus()).isEqualTo(ReconStatus.APPLIED);
        assertThat(txn.getWithdrawalRequest()).isSameAs(wr);
        // Chiều chi không bao giờ được chạm vào luồng thanh toán booking.
        verify(bookingPaymentHandler, never()).onPaymentConfirmed(any(), any(), anyString());
    }

    @Test
    void outgoingWithWrongAmount_queuedNotAutoPaid() {
        when(paymentTransactionRepository.existsByExternalId("casso-9")).thenReturn(false);
        when(withdrawalRequestRepository.findByRefCode("FMW77ABC123"))
                .thenReturn(Optional.of(withdrawal(WithdrawalStatus.APPROVED, "500000")));

        int matched = service.processCasso(
                oneItem("casso-9", new BigDecimal("-450000"), "CK FMW77ABC123"));

        // Chi sai số tiền là việc không được đoán — người thật phải xem.
        assertThat(matched).isZero();
        verify(withdrawalService, never()).markPaidByReconciliation(any(), anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.PAYOUT_AMOUNT_MISMATCH);
    }

    @Test
    void outgoingForAlreadyPaidWithdrawal_queuedAsStateMismatch() {
        when(paymentTransactionRepository.existsByExternalId("casso-10")).thenReturn(false);
        when(withdrawalRequestRepository.findByRefCode("FMW77ABC123"))
                .thenReturn(Optional.of(withdrawal(WithdrawalStatus.PAID, "500000")));

        int matched = service.processCasso(
                oneItem("casso-10", new BigDecimal("-500000"), "CK FMW77ABC123"));

        assertThat(matched).isZero();
        verify(withdrawalService, never()).markPaidByReconciliation(any(), anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.PAYOUT_STATE_MISMATCH);
    }

    @Test
    void outgoingWithoutPayoutRef_queuedAsPayoutUnmatched() {
        when(paymentTransactionRepository.existsByExternalId("casso-11")).thenReturn(false);

        int matched = service.processCasso(
                oneItem("casso-11", new BigDecimal("-500000"), "thanh toan dich vu"));

        assertThat(matched).isZero();
        // Không được rơi nhầm sang nhánh tiền vào và tra payment_orders.
        verify(paymentOrderRepository, never()).findByRefCode(anyString());
        assertThat(savedTxn().getAnomaly()).isEqualTo(PaymentTxnAnomaly.PAYOUT_UNMATCHED);
    }

    @Test
    void incomingRefPattern_doesNotMatchPayoutRef() {
        // "FM" + chữ số: mã lệnh rút FMW... có 'W' sau FM nên không được lọt vào
        // nhánh tiền vào; ngược lại mã đơn thanh toán không được coi là lệnh rút.
        when(paymentTransactionRepository.existsByExternalId("casso-12")).thenReturn(false);
        when(paymentOrderRepository.findByRefCode("FM10ABCDEF")).thenReturn(Optional.empty());

        service.processCasso(oneItem("casso-12", new BigDecimal("100000"), "FM10ABCDEF"));

        PaymentTransaction txn = savedTxn();
        assertThat(txn.getDirection()).isEqualTo(PaymentTxnDirection.IN);
        assertThat(txn.getRefCode()).isEqualTo("FM10ABCDEF");
        verify(withdrawalRequestRepository, never()).findByRefCode(anyString());
    }
}
