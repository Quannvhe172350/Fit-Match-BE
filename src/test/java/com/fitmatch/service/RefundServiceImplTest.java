package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.RefundRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.RefundRequestRepository;
import com.fitmatch.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WalletService walletService;
    @Mock private SettlementService settlementService;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private RefundServiceImpl service;

    private Booking booking(BookingStatus status, SettlementStatus settlement) {
        return Booking.builder()
                .id(10L)
                .gymProfile(GymProfile.builder().id(5L).build())
                .status(status)
                .settlementStatus(settlement)
                .payableAmount(new BigDecimal("200.00"))
                .build();
    }

    @Test
    void autoCreate_heldRejectedBooking_opensFullRefundRequest() {
        Booking b = booking(BookingStatus.REJECTED, SettlementStatus.HELD);
        when(refundRequestRepository.existsByBooking_IdAndStatus(10L, RefundStatus.PENDING)).thenReturn(false);
        when(settlementService.heldAmountOf(b)).thenReturn(new BigDecimal("200.00"));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        service.autoCreate(b, "Gym rejected booking");

        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.REFUND_PENDING);
        verify(refundRequestRepository).save(any(RefundRequest.class));
    }

    @Test
    void autoCreate_noHeldFunds_silentlySkips() {
        Booking b = booking(BookingStatus.CANCELLED, SettlementStatus.NONE);

        service.autoCreate(b, "Customer cancelled");

        verify(refundRequestRepository, never()).save(any());
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
    }

    @Test
    void createForCustomer_wrongBookingStatus_throws() {
        Booking b = booking(BookingStatus.CONFIRMED, SettlementStatus.HELD);
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.createForCustomer("john", 10L, "changed my mind"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void approveAndExecute_fullRefund_marksBookingRefunded() {
        Booking b = booking(BookingStatus.REJECTED, SettlementStatus.REFUND_PENDING);
        RefundRequest request = RefundRequest.builder()
                .id(1L).booking(b).amount(new BigDecimal("200.00")).status(RefundStatus.PENDING).build();
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        RefundResponse res = service.approveAndExecute(1L, null, "ok", "finance");

        verify(walletService).refundFromHeld(5L, 10L, new BigDecimal("200.00"));
        verify(walletService, never()).moveToPending(any(), any(), any());
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED);
        assertThat(res.getStatus()).isEqualTo(RefundStatus.EXECUTED);
    }

    @Test
    void approveAndExecute_partialRefund_movesRetainedToPending() {
        Booking b = booking(BookingStatus.CANCELLED, SettlementStatus.REFUND_PENDING);
        RefundRequest request = RefundRequest.builder()
                .id(1L).booking(b).amount(new BigDecimal("200.00")).status(RefundStatus.PENDING).build();
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        service.approveAndExecute(1L, new BigDecimal("150.00"), "late cancel fee", "finance");

        verify(walletService).refundFromHeld(5L, 10L, new BigDecimal("150.00"));
        verify(walletService).moveToPending(5L, 10L, new BigDecimal("50.00"));
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
        assertThat(b.getSettlementAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void approveAndExecute_amountExceedsRequested_throws() {
        Booking b = booking(BookingStatus.REJECTED, SettlementStatus.REFUND_PENDING);
        RefundRequest request = RefundRequest.builder()
                .id(1L).booking(b).amount(new BigDecimal("200.00")).status(RefundStatus.PENDING).build();
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveAndExecute(1L, new BigDecimal("250.00"), null, "finance"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void reject_returnsSettlementToHeld() {
        Booking b = booking(BookingStatus.CANCELLED, SettlementStatus.REFUND_PENDING);
        RefundRequest request = RefundRequest.builder()
                .id(1L).booking(b).amount(new BigDecimal("200.00")).status(RefundStatus.PENDING).build();
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        RefundResponse res = service.reject(1L, "policy: no refund", "finance");

        assertThat(res.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.HELD);
    }
}
