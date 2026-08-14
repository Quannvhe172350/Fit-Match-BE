package com.fitmatch.service;

import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.config.PaymentProperties;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.impl.PaymentServiceImpl;
import com.fitmatch.service.support.TicketLifecycle;
import com.fitmatch.service.support.TicketPromotionReleaser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

/** Đơn thanh toán VietQR của vé và luồng hết hạn thanh toán. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final Long TICKET_ID = 5L;

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @Mock private TicketLifecycle ticketLifecycle;
    @Mock private TicketPromotionReleaser ticketPromotionReleaser;
    private PaymentProperties paymentProperties;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.getVietqr().setBankBin("970422");
        paymentProperties.getVietqr().setAccountNo("123456789");
        paymentProperties.getVietqr().setAccountName("FIT MATCH");
        paymentProperties.getVietqr().setTemplate("compact2");
        paymentProperties.getPayment().setOrderTtlHours(24);
        service = new PaymentServiceImpl(paymentOrderRepository, paymentProperties,
                notificationDispatcher, ticketLifecycle, ticketPromotionReleaser);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            PaymentOrder o = inv.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });
    }

    private Ticket ticket(BigDecimal payable, TicketStatus status) {
        return Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username("customer1").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 10 ngày").build())
                .gymProfile(GymProfile.builder().id(1L).gymName("Gym A").build())
                .dayCount(10).payableAmount(payable).status(status)
                .build();
    }

    @Test
    void createOrder_buildsVietQrWithWholeVndAmount() {
        service.createOrderForTicket(ticket(BigDecimal.valueOf(1_000_000), TicketStatus.PENDING_PAYMENT));

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository).save(captor.capture());
        PaymentOrder order = captor.getValue();
        // P1-26: số tiền trên QR là số nguyên VND, nếu không webhook luôn thấy "trả thiếu".
        assertThat(order.getQrContent()).contains("amount=1000000");
        // refCode phải khớp regex FM\d+[0-9A-F]{6} của PaymentWebhookServiceImpl.
        assertThat(order.getRefCode()).matches("FM\\d+[0-9A-F]{6}");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createOrder_zeroPayable_isRejected() {
        assertThatThrownBy(() -> service.createOrderForTicket(
                ticket(BigDecimal.ZERO, TicketStatus.PENDING_PAYMENT)))
                .isInstanceOf(BusinessException.class);
        verify(paymentOrderRepository, never()).save(any());
    }

    /** Một vé chỉ có một đơn: gọi lại trả về đơn cũ thay vì sinh refCode mới. */
    @Test
    void createOrder_isIdempotentPerTicket() {
        PaymentOrder existing = PaymentOrder.builder().id(7L).refCode("FM5ABCDEF")
                .amount(BigDecimal.valueOf(1_000_000)).status(PaymentStatus.PENDING).build();
        when(paymentOrderRepository.findByTicket_Id(TICKET_ID)).thenReturn(Optional.of(existing));

        var response = service.createOrderForTicket(
                ticket(BigDecimal.valueOf(1_000_000), TicketStatus.PENDING_PAYMENT));

        assertThat(response.getRefCode()).isEqualTo("FM5ABCDEF");
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void expireOverdue_cancelsTicketAndReleasesPromotions() {
        Ticket ticket = ticket(BigDecimal.valueOf(1_000_000), TicketStatus.PENDING_PAYMENT);
        PaymentOrder order = PaymentOrder.builder().id(7L).ticket(ticket)
                .status(PaymentStatus.PENDING).amount(BigDecimal.valueOf(1_000_000))
                .expiresAt(LocalDateTime.now().minusHours(1)).build();
        when(paymentOrderRepository.findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(order));

        int cancelled = service.expireOverdueOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(ticketLifecycle).transition(eq(ticket), eq(TicketStatus.CANCELLED), any());
        // Khách chưa trả tiền mà để QR hết hạn thì KHÔNG được mất điểm/lượt voucher.
        verify(ticketPromotionReleaser).release(ticket);
        verify(notificationDispatcher).ticketPaymentExpired(ticket);
    }

    /** Vé đã ACTIVE (tiền về muộn nhưng đã xác nhận tay) không bị huỷ theo đơn. */
    @Test
    void expireOverdue_activeTicket_isNotCancelled() {
        Ticket ticket = ticket(BigDecimal.valueOf(1_000_000), TicketStatus.ACTIVE);
        PaymentOrder order = PaymentOrder.builder().id(7L).ticket(ticket)
                .status(PaymentStatus.PENDING).amount(BigDecimal.valueOf(1_000_000))
                .expiresAt(LocalDateTime.now().minusHours(1)).build();
        when(paymentOrderRepository.findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(order));

        assertThat(service.expireOverdueOrders()).isZero();
        verify(ticketLifecycle, never()).transition(any(), any(), any());
        verify(ticketPromotionReleaser, never()).release(any());
    }

    @Test
    void cancelOrderIfPending_closesOnlyPendingOrders() {
        PaymentOrder order = PaymentOrder.builder().id(7L).status(PaymentStatus.PENDING).build();
        when(paymentOrderRepository.findByTicket_Id(TICKET_ID)).thenReturn(Optional.of(order));

        service.cancelTicketOrderIfPending(TICKET_ID);

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancelOrderIfPending_paidOrder_isUntouched() {
        PaymentOrder order = PaymentOrder.builder().id(7L).status(PaymentStatus.PAID).build();
        when(paymentOrderRepository.findByTicket_Id(TICKET_ID)).thenReturn(Optional.of(order));

        service.cancelTicketOrderIfPending(TICKET_ID);

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
