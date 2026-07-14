package com.fitmatch.service;

import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.CommissionConfig;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private WalletService walletService;
    @Mock private CommissionConfigService commissionConfigService;
    @Mock private AuditService auditService;
    @InjectMocks private SettlementServiceImpl service;

    private Booking heldBooking() {
        return Booking.builder()
                .id(10L)
                .gymProfile(GymProfile.builder().id(5L).build())
                .payableAmount(new BigDecimal("200.00"))
                .settlementStatus(SettlementStatus.HELD)
                .build();
    }

    @Test
    void settleAfterFulfillment_heldFunds_moveToPending() {
        Booking booking = heldBooking();
        when(paymentOrderRepository.findByBooking_Id(10L)).thenReturn(Optional.of(
                PaymentOrder.builder().booking(booking)
                        .amount(new BigDecimal("200.00")).status(PaymentStatus.PAID).build()));

        service.settleAfterFulfillment(booking, "session completed");

        verify(walletService).moveToPending(5L, 10L, new BigDecimal("200.00"));
        assertThat(booking.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
        assertThat(booking.getSettlementAmount()).isEqualByComparingTo("200.00");
        assertThat(booking.getSettlementPendingAt()).isNotNull();
    }

    @Test
    void settleAfterFulfillment_noHeldFunds_skips() {
        Booking booking = heldBooking();
        booking.setSettlementStatus(SettlementStatus.NONE);

        service.settleAfterFulfillment(booking, "free booking");

        verifyNoInteractions(walletService);
        assertThat(booking.getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
    }

    @Test
    void releaseOne_dueBooking_releasesWithCommission() {
        Booking booking = heldBooking();
        booking.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
        booking.setSettlementAmount(new BigDecimal("200.00"));
        booking.setSettlementPendingAt(LocalDateTime.now().minusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(commissionConfigService.currentConfig()).thenReturn(CommissionConfig.builder()
                .commissionPercent(new BigDecimal("15.00"))
                .platformFeePercent(BigDecimal.ZERO)
                .settlementHoldDays(3)
                .build());

        service.releaseOne(10L);

        verify(walletService).release(eq(5L), eq(10L),
                eq(new BigDecimal("200.00")), eq(new BigDecimal("15.00")));
        assertThat(booking.getSettlementStatus()).isEqualTo(SettlementStatus.RELEASED);
    }

    @Test
    void releaseOne_alreadyReleased_isIdempotent() {
        Booking booking = heldBooking();
        booking.setSettlementStatus(SettlementStatus.RELEASED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        service.releaseOne(10L);

        verifyNoInteractions(walletService);
        verify(bookingRepository, never()).save(any());
    }
}
