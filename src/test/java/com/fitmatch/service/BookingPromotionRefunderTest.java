package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.service.support.BookingPromotionRefunder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingPromotionRefunderTest {

    @Mock private LoyaltyService loyaltyService;
    @Mock private VoucherService voucherService;
    @InjectMocks private BookingPromotionRefunder refunder;

    @Test
    void checkedOutThenCancelled_refundsOnceAndSetsFlag() {
        Booking b = Booking.builder().id(10L).status(BookingStatus.CANCELLED).build();

        refunder.releaseOnCancellation(b, BookingStatus.PENDING_PAYMENT);

        verify(loyaltyService).refundToBooking(b);
        verify(voucherService).releaseFromBooking(b);
        assertThat(b.isPromoReleased()).isTrue();
    }

    @Test
    void cancelledFromDraft_neverConsumed_noRefund() {
        Booking b = Booking.builder().id(10L).status(BookingStatus.CANCELLED).build();

        refunder.releaseOnCancellation(b, BookingStatus.DRAFT);

        verify(loyaltyService, never()).refundToBooking(b);
        verify(voucherService, never()).releaseFromBooking(b);
        assertThat(b.isPromoReleased()).isFalse();
    }

    @Test
    void alreadyReleased_isIdempotent() {
        Booking b = Booking.builder().id(10L).status(BookingStatus.CANCELLED).promoReleased(true).build();

        refunder.releaseOnCancellation(b, BookingStatus.CONFIRMED);

        verify(loyaltyService, never()).refundToBooking(b);
        verify(voucherService, never()).releaseFromBooking(b);
    }
}
