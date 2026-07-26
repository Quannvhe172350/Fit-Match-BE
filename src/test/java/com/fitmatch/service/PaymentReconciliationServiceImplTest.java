package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.PaymentTransaction;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.PaymentTransactionRepository;
import com.fitmatch.service.impl.PaymentReconciliationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đối soát thủ công tiền vào không khớp booking (UC-053/056). Trọng tâm: không
 * được hold tiền hai lần, không áp vào booking đã đóng, và mọi quyết định đều
 * chuyển giao dịch ra khỏi hàng đợi NEEDS_REVIEW.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceImplTest {

    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AdminBookingService adminBookingService;
    @Mock private AuditService auditService;
    @InjectMocks private PaymentReconciliationServiceImpl service;

    private static final BigDecimal PAYABLE = new BigDecimal("100000");

    private PaymentTransaction txn(ReconStatus status, BigDecimal amount) {
        return PaymentTransaction.builder()
                .id(7L).externalId("casso-x").amount(amount)
                .anomaly(PaymentTxnAnomaly.UNMATCHED).reconStatus(status)
                .build();
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder().id(1L).status(status).payableAmount(PAYABLE).build();
    }

    private PaymentOrder order(PaymentStatus status) {
        return PaymentOrder.builder().id(5L).refCode("FM1ABCDEF")
                .amount(PAYABLE).status(status).expiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    void apply_happyPath_delegatesToConfirmHoldAndClosesQueueItem() {
        PaymentTransaction t = txn(ReconStatus.NEEDS_REVIEW, PAYABLE);
        PaymentOrder o = order(PaymentStatus.PENDING);
        when(paymentTransactionRepository.findById(7L)).thenReturn(Optional.of(t));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT)));
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(o));

        var result = service.applyToBooking(7L, 1L, false, "khách CK sai nội dung", "finance1");

        // Đi qua đúng luồng hold ví/đơn PAID/notify của Admin confirm-payment,
        // không tự viết lại phép giữ tiền lần hai.
        verify(adminBookingService).confirmPaymentHold(1L, "finance1");
        assertThat(t.getReconStatus()).isEqualTo(ReconStatus.RESOLVED_APPLIED);
        assertThat(t.getPaymentOrder()).isSameAs(o);
        assertThat(t.getResolvedBy()).isEqualTo("finance1");
        assertThat(t.getResolvedAt()).isNotNull();
        assertThat(result.getReconStatus()).isEqualTo(ReconStatus.RESOLVED_APPLIED);
        verify(auditService).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void apply_bookingNotAwaitingPayment_rejected() {
        when(paymentTransactionRepository.findById(7L))
                .thenReturn(Optional.of(txn(ReconStatus.NEEDS_REVIEW, PAYABLE)));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.CANCELLED)));

        assertThatThrownBy(() -> service.applyToBooking(7L, 1L, false, null, "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(adminBookingService, never()).confirmPaymentHold(any(), anyString());
    }

    @Test
    void apply_orderAlreadyPaid_rejected() {
        when(paymentTransactionRepository.findById(7L))
                .thenReturn(Optional.of(txn(ReconStatus.NEEDS_REVIEW, PAYABLE)));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT)));
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(order(PaymentStatus.PAID)));

        assertThatThrownBy(() -> service.applyToBooking(7L, 1L, false, null, "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(adminBookingService, never()).confirmPaymentHold(any(), anyString());
    }

    @Test
    void apply_amountShortWithoutOverride_rejected() {
        when(paymentTransactionRepository.findById(7L))
                .thenReturn(Optional.of(txn(ReconStatus.NEEDS_REVIEW, new BigDecimal("50000"))));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT)));
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(order(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> service.applyToBooking(7L, 1L, false, null, "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(adminBookingService, never()).confirmPaymentHold(any(), anyString());
    }

    @Test
    void apply_amountShortOverrideNeedsNote() {
        when(paymentTransactionRepository.findById(7L))
                .thenReturn(Optional.of(txn(ReconStatus.NEEDS_REVIEW, new BigDecimal("50000"))));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT)));
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(order(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> service.applyToBooking(7L, 1L, true, "  ", "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(adminBookingService, never()).confirmPaymentHold(any(), anyString());
    }

    @Test
    void apply_amountShortOverrideWithNote_accepted() {
        PaymentTransaction t = txn(ReconStatus.NEEDS_REVIEW, new BigDecimal("50000"));
        when(paymentTransactionRepository.findById(7L)).thenReturn(Optional.of(t));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT)));
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(order(PaymentStatus.PENDING)));

        service.applyToBooking(7L, 1L, true, "khách CK 2 lần, sao kê đã đủ", "finance1");

        verify(adminBookingService).confirmPaymentHold(1L, "finance1");
        assertThat(t.getReconStatus()).isEqualTo(ReconStatus.RESOLVED_APPLIED);
    }

    @Test
    void apply_alreadyReconciled_rejected() {
        when(paymentTransactionRepository.findById(7L))
                .thenReturn(Optional.of(txn(ReconStatus.RESOLVED_REFUNDED, PAYABLE)));

        assertThatThrownBy(() -> service.applyToBooking(7L, 1L, false, null, "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(adminBookingService, never()).confirmPaymentHold(any(), anyString());
    }

    @Test
    void resolve_refunded_recordsDecision() {
        PaymentTransaction t = txn(ReconStatus.NEEDS_REVIEW, PAYABLE);
        when(paymentTransactionRepository.findById(7L)).thenReturn(Optional.of(t));

        var result = service.resolve(7L, ReconStatus.RESOLVED_REFUNDED, "CK trả lại ref 998877", "finance1");

        assertThat(t.getReconStatus()).isEqualTo(ReconStatus.RESOLVED_REFUNDED);
        assertThat(t.getResolutionNote()).isEqualTo("CK trả lại ref 998877");
        assertThat(t.getResolvedBy()).isEqualTo("finance1");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(auditService).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void resolve_appliedStatusNotAllowedAsManualOutcome() {
        // Gắn booking phải đi qua applyToBooking (có hold ví); không được "chốt tay"
        // thành RESOLVED_APPLIED mà tiền không bao giờ vào ví.
        assertThatThrownBy(() ->
                service.resolve(7L, ReconStatus.RESOLVED_APPLIED, "note", "finance1"))
                .isInstanceOf(BusinessException.class);

        verify(paymentTransactionRepository, never()).save(any());
    }
}
