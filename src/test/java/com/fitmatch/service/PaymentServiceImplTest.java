package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.config.PaymentProperties;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.impl.PaymentServiceImpl;
import com.fitmatch.service.support.BookingLifecycle;
import com.fitmatch.service.support.BookingPromotionRefunder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phủ PaymentServiceImpl (UC-052/054) — trước đây 0 test: idempotency đơn,
 * chặn booking 0đ, và luồng hết hạn thanh toán (đóng booking + hoàn điểm/voucher).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentProperties paymentProperties;
    @Mock private BookingLifecycle bookingLifecycle;
    @Mock private BookingPromotionRefunder promotionRefunder;
    @InjectMocks private PaymentServiceImpl service;

    @Test
    void createOrder_idempotent_returnsExisting() {
        Booking booking = Booking.builder().id(1L).payableAmount(new BigDecimal("100000")).build();
        PaymentOrder existing = PaymentOrder.builder().id(9L).booking(booking)
                .refCode("FM1ABCDEF").amount(new BigDecimal("100000")).status(PaymentStatus.PENDING).build();
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(existing));

        service.createOrder(booking);

        // Không tạo order mới khi đã tồn tại.
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void createOrder_noPayable_throws() {
        Booking booking = Booking.builder().id(1L).payableAmount(BigDecimal.ZERO).build();

        assertThatThrownBy(() -> service.createOrder(booking))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ERROR);
    }

    @Test
    void expireOverdueOrders_cancelsBookingAndReleasesPromo() {
        Booking booking = Booking.builder().id(1L).status(BookingStatus.PENDING_PAYMENT).build();
        PaymentOrder overdue = PaymentOrder.builder().id(9L).booking(booking)
                .refCode("FM1ABCDEF").amount(new BigDecimal("100000")).status(PaymentStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(paymentOrderRepository.findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(overdue));

        int cancelled = service.expireOverdueOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(bookingLifecycle).transition(eq(booking), eq(BookingStatus.CANCELLED), any());
        // UC-073: hoàn điểm/voucher đã tiêu ở checkout khi để QR hết hạn.
        verify(promotionRefunder).releaseOnCancellation(booking, BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void cancelOrderIfPending_setsCancelled() {
        PaymentOrder pending = PaymentOrder.builder().id(9L)
                .refCode("FM1ABCDEF").amount(new BigDecimal("100000")).status(PaymentStatus.PENDING).build();
        when(paymentOrderRepository.findByBooking_Id(1L)).thenReturn(Optional.of(pending));

        service.cancelOrderIfPending(1L);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentOrderRepository).save(pending);
    }
}
