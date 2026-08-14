package com.fitmatch.service;

import com.fitmatch.common.enums.RefundMode;
import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.ticket.ApproveTicketRefundRequest;
import com.fitmatch.dto.ticket.TicketRefundPreviewResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.RefundRequest;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.RefundRequestRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.impl.TicketRefundServiceImpl;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PartialRefundCalculator;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.TicketLifecycle;
import com.fitmatch.service.support.TicketPromotionReleaser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Câu 11/12/13/32: mọi yêu cầu qua admin, hai mức hoàn, huỷ buổi tương lai. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketRefundServiceImplTest {

    private static final String USERNAME = "customer1";
    private static final Long TICKET_ID = 100L;
    private static final Long REFUND_ID = 500L;
    private static final Long GYM_ID = 1L;

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TrainingSessionRepository sessionRepository;
    @Spy private PartialRefundCalculator refundCalculator = new PartialRefundCalculator();
    @Mock private WalletService walletService;
    @Mock private SettlementService settlementService;
    @Mock private TicketLifecycle ticketLifecycle;
    @Mock private SessionLifecycle sessionLifecycle;
    @Mock private TicketPromotionReleaser promotionReleaser;
    @Mock private AuditService auditService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private TicketRefundServiceImpl service;

    @BeforeEach
    void setUp() {
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(REFUND_ID);
            return r;
        });
        when(sessionRepository.findByTicket_IdAndStatusAndSessionDateGreaterThanEqual(
                any(), any(), any())).thenReturn(List.of());
    }

    private Ticket ticket(TicketStatus status, int dayCount, LocalDate startDate) {
        Ticket t = Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username(USERNAME).fullName("Khách A").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 10 ngày").build())
                .gymProfile(GymProfile.builder().id(GYM_ID).gymName("Gym A").build())
                .kind(TicketKind.PACKAGE)
                .dayCount(dayCount)
                .startDate(startDate)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .status(status)
                .settlementStatus(SettlementStatus.HELD)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        when(ticketRepository.findByIdAndCustomer_Username(TICKET_ID, USERNAME))
                .thenReturn(Optional.of(t));
        return t;
    }

    private RefundRequest pendingRequest(Ticket ticket) {
        ticket.setSettlementStatus(SettlementStatus.REFUND_PENDING);
        RefundRequest r = RefundRequest.builder()
                .id(REFUND_ID).ticket(ticket)
                .amount(ticket.getPayableAmount())
                .status(RefundStatus.PENDING)
                .build();
        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(r));
        return r;
    }

    // ---------- tạo yêu cầu ----------

    @Test
    void request_activeTicket_opensPendingAndBlocksAutoRelease() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        when(settlementService.heldAmountOfTicket(t)).thenReturn(BigDecimal.valueOf(1_000_000));

        RefundResponse response = service.requestByCustomer(USERNAME, TICKET_ID, "Đổi ý");

        assertThat(response.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.REFUND_PENDING);
    }

    /** Câu 32: vé hết hạn thì tiền đã về gym — từ chối kèm lý do rõ ràng. */
    @Test
    void request_expiredTicket_isRejectedWithExplanation() {
        ticket(TicketStatus.EXPIRED, 10, LocalDate.now().minusDays(40));

        assertThatThrownBy(() -> service.requestByCustomer(USERNAME, TICKET_ID, "Đòi hoàn"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hạn")
                .hasMessageContaining("tranh chấp");
    }

    @Test
    void request_duplicatePending_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now());
        when(settlementService.heldAmountOfTicket(t)).thenReturn(BigDecimal.valueOf(1_000_000));
        when(refundRequestRepository.existsByTicket_IdAndStatus(TICKET_ID, RefundStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestByCustomer(USERNAME, TICKET_ID, "Đổi ý"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang chờ duyệt");
    }

    // ---------- preview ----------

    @Test
    void preview_showsBothOptionsAndElapsedDays() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(t);

        TicketRefundPreviewResponse preview = service.preview(REFUND_ID);

        assertThat(preview.getElapsedDays()).isEqualTo(3);
        assertThat(preview.getFullRefund()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(preview.getPartialRefund()).isEqualByComparingTo(BigDecimal.valueOf(700_000));
        assertThat(preview.getRetained()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        assertThat(preview.isFullRefundOnly()).isFalse();
    }

    /** Câu 13: chưa dùng ngày nào -> FE chỉ hiện một lựa chọn. */
    @Test
    void preview_unusedTicket_flagsFullRefundOnly() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, null);
        pendingRequest(t);

        TicketRefundPreviewResponse preview = service.preview(REFUND_ID);

        assertThat(preview.isFullRefundOnly()).isTrue();
        assertThat(preview.getPartialRefund()).isEqualByComparingTo(preview.getFullRefund());
    }

    // ---------- duyệt ----------

    @Test
    void approve_partialElapsed_splitsMoneyAndKeepsItConserved() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        RefundRequest request = pendingRequest(t);

        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.PARTIAL_ELAPSED).build(), "admin1");

        verify(walletService).refundToCustomerForTicket(eq(GYM_ID), any(), eq(TICKET_ID),
                eq(BigDecimal.valueOf(700_000)));
        verify(walletService).moveToPendingForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(300_000));
        assertThat(request.getRetainedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        assertThat(request.getElapsedDays()).isEqualTo(3);
        assertThat(request.getRefundMode()).isEqualTo(RefundMode.PARTIAL_ELAPSED);
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
    }

    @Test
    void approve_full_refundsEverythingAndRetainsNothing() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(4));
        pendingRequest(t);

        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.FULL).build(), "admin1");

        verify(walletService).refundToCustomerForTicket(eq(GYM_ID), any(), eq(TICKET_ID),
                eq(BigDecimal.valueOf(1_000_000)));
        verify(walletService, never()).moveToPendingForTicket(any(), any(), any());
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED);
    }

    /** Điểm/voucher chỉ hoàn khi hoàn TOÀN BỘ — hoàn một phần là đã dùng một phần. */
    @Test
    void approve_full_releasesPromotions_partialDoesNot() {
        Ticket full = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(full);
        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.FULL).build(), "admin1");
        verify(promotionReleaser).release(full);

        Ticket partial = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(partial);
        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.PARTIAL_ELAPSED).build(), "admin1");
        verify(promotionReleaser, never()).release(partial);
    }

    /** Câu 12: duyệt hoàn là huỷ sạch buổi tập tương lai. */
    @Test
    void approve_cancelsEveryFutureSession() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(t);
        when(sessionRepository.findByTicket_IdAndStatusAndSessionDateGreaterThanEqual(
                eq(TICKET_ID), eq(SessionStatus.SCHEDULED), any()))
                .thenReturn(List.of(
                        TrainingSession.builder().id(1L).status(SessionStatus.SCHEDULED)
                                .sessionDate(LocalDate.now().plusDays(1)).build(),
                        TrainingSession.builder().id(2L).status(SessionStatus.SCHEDULED)
                                .sessionDate(LocalDate.now().plusDays(2)).build()));

        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.FULL).build(), "admin1");

        verify(sessionLifecycle, org.mockito.Mockito.times(2))
                .transition(any(), eq(SessionStatus.CANCELLED), any());
    }

    @Test
    void approve_movesTicketToRefunded() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(t);

        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.FULL).build(), "admin1");

        verify(ticketLifecycle).transition(eq(t), eq(TicketStatus.REFUNDED), any());
    }

    /** Chống double-spend: vé đang tranh chấp thì refund không được trừ held lần nữa. */
    @Test
    void approve_whenTicketIsDisputed_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(t);
        t.setSettlementStatus(SettlementStatus.DISPUTED);

        assertThatThrownBy(() -> service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.FULL).build(), "admin1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tranh chấp");
        verify(walletService, never()).refundToCustomerForTicket(any(), any(), any(), any());
    }

    // ---------- từ chối ----------

    @Test
    void reject_returnsFundsToHeld() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        RefundRequest request = pendingRequest(t);

        service.reject(REFUND_ID, "Không đủ căn cứ", "admin1");

        assertThat(request.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.HELD);
        verify(notificationDispatcher).ticketRefundRejected(eq(t), any(), eq("Không đủ căn cứ"));
    }

    @Test
    void reject_alreadyExecuted_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now());
        RefundRequest request = pendingRequest(t);
        request.setStatus(RefundStatus.EXECUTED);

        assertThatThrownBy(() -> service.reject(REFUND_ID, "note", "admin1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không còn ở trạng thái chờ duyệt");
    }

    /** Yêu cầu của luồng booking cũ không được đi nhầm vào đường của vé. */
    @Test
    void legacyBookingRequest_isRefusedByTicketFlow() {
        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(
                RefundRequest.builder().id(REFUND_ID).status(RefundStatus.PENDING).build()));

        assertThatThrownBy(() -> service.preview(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("luồng booking cũ");
    }

    @Test
    void approve_recordsDecisionNoteWithElapsedDays() {
        Ticket t = ticket(TicketStatus.ACTIVE, 10, LocalDate.now().minusDays(2));
        pendingRequest(t);

        service.approve(REFUND_ID, ApproveTicketRefundRequest.builder()
                .mode(RefundMode.PARTIAL_ELAPSED).note("Khách chuyển nhà").build(), "admin1");

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDecisionNote())
                .contains("3/10 ngày đã qua")
                .contains("Khách chuyển nhà");
    }
}
