package com.fitmatch.service;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.support.DisputeFinancialApplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisputeFinancialApplierTest {

    @Mock private WalletService walletService;
    @Mock private BookingRepository bookingRepository;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private DisputeFinancialApplier applier;

    private static final User CUSTOMER = User.builder().id(42L).username("john").build();

    private Dispute dispute(BigDecimal frozen) {
        Booking b = Booking.builder().id(10L)
                .gymProfile(GymProfile.builder().id(5L).build())
                .customer(CUSTOMER)
                .settlementStatus(SettlementStatus.DISPUTED)
                .build();
        return Dispute.builder().id(1L).booking(b).frozenAmount(frozen).build();
    }

    @Test
    void refundFull_refundsAllAndMarksRefunded() {
        Dispute d = dispute(new BigDecimal("200.00"));

        applier.apply(d, DisputeResolution.REFUND_FULL, null);

        verify(walletService).refundToCustomer(5L, CUSTOMER, 10L, new BigDecimal("200.00"));
        verify(walletService, never()).moveToPending(any(), any(), any());
        assertThat(d.getBooking().getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED);
    }

    @Test
    void releaseToGym_movesAllToPending() {
        Dispute d = dispute(new BigDecimal("200.00"));

        applier.apply(d, DisputeResolution.RELEASE_TO_GYM, null);

        verify(walletService).moveToPending(5L, 10L, new BigDecimal("200.00"));
        verify(walletService, never()).refundToCustomer(any(), any(), any(), any());
        assertThat(d.getBooking().getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
    }

    @Test
    void split_refundsPortionAndReleasesRemainder() {
        Dispute d = dispute(new BigDecimal("200.00"));

        applier.apply(d, DisputeResolution.SPLIT, new BigDecimal("120.00"));

        verify(walletService).refundToCustomer(5L, CUSTOMER, 10L, new BigDecimal("120.00"));
        verify(walletService).moveToPending(5L, 10L, new BigDecimal("80.00"));
        assertThat(d.getBooking().getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
        assertThat(d.getBooking().getSettlementAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void partial_amountExceedsHeld_throws() {
        Dispute d = dispute(new BigDecimal("200.00"));

        assertThatThrownBy(() -> applier.apply(d, DisputeResolution.REFUND_PARTIAL, new BigDecimal("250.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void noHeldFunds_recordsDecisionOnly() {
        Dispute d = dispute(BigDecimal.ZERO);

        applier.apply(d, DisputeResolution.REFUND_FULL, null);

        verify(walletService, never()).refundToCustomer(any(), any(), any(), any());
        assertThat(d.getBooking().getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
    }
}
