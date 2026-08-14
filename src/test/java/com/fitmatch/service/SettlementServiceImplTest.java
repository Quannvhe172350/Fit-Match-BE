package com.fitmatch.service;

import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.CommissionConfig;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.User;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Câu 15 + 32: mốc giải ngân là vé USED_UP hoặc EXPIRED. Số tiền chuyển sang
 * pending phải là số THỰC ĐÃ NHẬN (đơn PAID), không phải payableAmount trên vé.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementServiceImplTest {

    private static final Long TICKET_ID = 10L;
    private static final Long GYM_ID = 1L;

    @Mock private TicketRepository ticketRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private WalletService walletService;
    @Mock private CommissionConfigService commissionConfigService;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @Mock private com.fitmatch.service.support.DisputeWindow disputeWindow;
    @InjectMocks private SettlementServiceImpl service;

    private Ticket heldTicket() {
        return Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username("customer1").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 10 ngày").build())
                .gymProfile(GymProfile.builder().id(GYM_ID).gymName("Gym A").build())
                .dayCount(10)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .settlementStatus(SettlementStatus.HELD)
                .build();
    }

    private void commission(String percent, int holdDays) {
        when(commissionConfigService.currentConfig()).thenReturn(CommissionConfig.builder()
                .commissionPercent(new BigDecimal(percent))
                .platformFeePercent(BigDecimal.ZERO)
                .settlementHoldDays(holdDays)
                .build());
    }

    @Test
    void settle_heldFunds_moveToPendingAndSnapshotCommission() {
        Ticket ticket = heldTicket();
        commission("10.00", 3);
        when(paymentOrderRepository.findByTicket_Id(TICKET_ID)).thenReturn(Optional.of(
                PaymentOrder.builder().status(PaymentStatus.PAID)
                        .amount(BigDecimal.valueOf(1_000_000)).build()));

        service.settleTicketAfterFulfillment(ticket, "Ticket used up");

        verify(walletService).moveToPendingForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(1_000_000));
        assertThat(ticket.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
        assertThat(ticket.getSettlementAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(ticket.getSettlementPendingAt()).isNotNull();
        // P1-7: chốt % hoa hồng ngay để giải ngân về sau không bị áp hồi tố.
        assertThat(ticket.getCommissionPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    /** Vé miễn phí (điểm/voucher phủ hết) không giữ đồng nào — không có gì để chuyển. */
    @Test
    void settle_noHeldFunds_skips() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.NONE);

        service.settleTicketAfterFulfillment(ticket, "Ticket used up");

        verify(walletService, never()).moveToPendingForTicket(any(), any(), any());
    }

    /** Idempotent: chạy lần hai khi đã PENDING_RELEASE thì không chuyển tiếp. */
    @Test
    void settle_twice_isIdempotent() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);

        service.settleTicketAfterFulfillment(ticket, "Ticket expired");

        verify(walletService, never()).moveToPendingForTicket(any(), any(), any());
    }

    /**
     * Số tiền giải ngân lấy từ đơn thanh toán PAID chứ không từ payableAmount:
     * khách chuyển thừa thì phần thừa đã nằm trong ví, phải theo đúng sổ sách.
     */
    @Test
    void settle_usesPaidOrderAmountNotTicketPayable() {
        Ticket ticket = heldTicket();
        commission("10.00", 3);
        when(paymentOrderRepository.findByTicket_Id(TICKET_ID)).thenReturn(Optional.of(
                PaymentOrder.builder().status(PaymentStatus.PAID)
                        .amount(BigDecimal.valueOf(1_200_000)).build()));

        service.settleTicketAfterFulfillment(ticket, "Ticket used up");

        verify(walletService).moveToPendingForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(1_200_000));
    }

    @Test
    void release_usesSnapshotCommissionNotCurrentConfig() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
        ticket.setSettlementAmount(BigDecimal.valueOf(1_000_000));
        ticket.setCommissionPercent(new BigDecimal("10.00"));
        ticket.setSettlementPendingAt(LocalDateTime.now().minusDays(5));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        // Admin đã đổi hoa hồng lên 30% SAU khi vé chuyển pending — không được áp.
        commission("30.00", 3);

        service.releaseTicket(TICKET_ID);

        verify(walletService).releaseForTicket(eq(GYM_ID), eq(TICKET_ID),
                eq(BigDecimal.valueOf(1_000_000)), eq(new BigDecimal("10.00")));
        assertThat(ticket.getSettlementStatus()).isEqualTo(SettlementStatus.RELEASED);
    }

    /** Job có thể chạy trùng; vé đã RELEASED thì bỏ qua chứ không giải ngân hai lần. */
    @Test
    void release_alreadyReleased_isIdempotent() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.RELEASED);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        service.releaseTicket(TICKET_ID);

        verify(walletService, never()).releaseForTicket(any(), any(), any(), any());
    }

    /**
     * D-18: hết hạn giữ tiền nhưng cửa sổ khiếu nại còn mở thì CHƯA giải ngân.
     * Đây là chốt chặn cho trường hợp admin đặt settlementHoldDays nhỏ hơn
     * dispute.open-window-days — tiền ra khỏi hệ thống trong khi khách vẫn còn
     * quyền mở tranh chấp thì chỉ còn đường thu hồi thủ công.
     */
    @Test
    void findDue_disputeWindowStillOpen_isHeldBack() {
        Ticket ticket = pendingReleaseTicket();
        commission("10.00", 1);
        when(ticketRepository.findBySettlementStatusAndSettlementPendingAtBefore(
                eq(SettlementStatus.PENDING_RELEASE), any())).thenReturn(java.util.List.of(ticket));
        when(disputeWindow.releaseAllowed(ticket)).thenReturn(false);

        assertThat(service.findTicketsDueForRelease()).isEmpty();
    }

    @Test
    void findDue_disputeWindowClosed_isReleasable() {
        Ticket ticket = pendingReleaseTicket();
        commission("10.00", 7);
        when(ticketRepository.findBySettlementStatusAndSettlementPendingAtBefore(
                eq(SettlementStatus.PENDING_RELEASE), any())).thenReturn(java.util.List.of(ticket));
        when(disputeWindow.releaseAllowed(ticket)).thenReturn(true);

        assertThat(service.findTicketsDueForRelease()).containsExactly(TICKET_ID);
    }

    private Ticket pendingReleaseTicket() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
        ticket.setSettlementAmount(BigDecimal.valueOf(1_000_000));
        ticket.setSettlementPendingAt(LocalDateTime.now().minusDays(8));
        return ticket;
    }

    /** Vé đang tranh chấp bị kéo về DISPUTED — scheduler không được giải ngân. */
    @Test
    void release_disputedTicket_isSkipped() {
        Ticket ticket = heldTicket();
        ticket.setSettlementStatus(SettlementStatus.DISPUTED);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        service.releaseTicket(TICKET_ID);

        verify(walletService, never()).releaseForTicket(any(), any(), any(), any());
    }
}
