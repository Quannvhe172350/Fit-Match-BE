package com.fitmatch.service;

import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.TicketDisputeServiceImpl;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Câu 34: tranh chấp CẤP VÉ đóng băng toàn bộ phần đang giữ; tranh chấp CẤP
 * BUỔI chỉ đóng băng giá trị một ngày (payableAmount / dayCount).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketDisputeServiceImplTest {

    private static final Long TICKET_ID = 10L;
    private static final Long SESSION_ID = 500L;
    private static final Long GYM_ID = 1L;

    @Mock private DisputeRepository disputeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TrainingSessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private SettlementService settlementService;
    @Mock private AuditService auditService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private TicketDisputeServiceImpl service;

    @BeforeEach
    void setUp() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(
                User.builder().id(9L).username("customer1").role(Role.ROLE_CUSTOMER).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            if (d.getId() == null) d.setId(1L);
            return d;
        });
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(TICKET_ID)).thenReturn(List.of());
    }

    private Ticket ticket(TicketStatus status, SettlementStatus settlement) {
        Ticket t = Ticket.builder().id(TICKET_ID)
                .customer(User.builder().id(9L).username("customer1").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 10 ngày").build())
                .gymProfile(GymProfile.builder().id(GYM_ID).gymName("Gym A")
                        .user(User.builder().id(2L).username("gym1").build()).build())
                .gymBranch(GymBranch.builder().id(22L).name("Chi nhánh 1").build())
                .dayCount(10)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .status(status).settlementStatus(settlement)
                .expiresAt(java.time.LocalDateTime.now().plusDays(30))
                .build();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(t));
        when(settlementService.heldAmountOfTicket(t)).thenReturn(BigDecimal.valueOf(1_000_000));
        return t;
    }

    private TrainingSession session(Ticket t, SessionStatus status) {
        TrainingSession s = TrainingSession.builder()
                .id(SESSION_ID).ticket(t).status(status)
                .sessionDate(LocalDate.now().minusDays(1)).dayIndex(1)
                .ptProfile(PtProfile.builder().id(11L).displayName("PT A")
                        .user(User.builder().id(5L).username("pt1").build()).build())
                .build();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        return s;
    }

    // ---------- cấp vé ----------

    @Test
    void ticketLevel_freezesEntireHeldAmount() {
        Ticket t = ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);

        var response = service.open("customer1", TICKET_ID, null, "Gym đóng cửa");

        assertThat(response.getSessionId()).isNull();
        assertThat(response.getFrozenAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
    }

    /** Tiền đã sang pending thì phải kéo NGƯỢC về held để scheduler không release. */
    @Test
    void ticketLevel_pendingRelease_pullsFundsBackToHeld() {
        Ticket t = ticket(TicketStatus.USED_UP, SettlementStatus.PENDING_RELEASE);
        t.setSettlementAmount(BigDecimal.valueOf(1_000_000));

        service.open("customer1", TICKET_ID, null, "PT không đến buổi nào");

        verify(walletService).reverseToHeldForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(1_000_000));
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
    }

    /** Tiền đã giải ngân xong thì không còn gì để bảo vệ — frozen = 0. */
    @Test
    void ticketLevel_alreadyReleased_freezesNothing() {
        ticket(TicketStatus.USED_UP, SettlementStatus.RELEASED);

        var response = service.open("customer1", TICKET_ID, null, "khiếu nại muộn");

        assertThat(response.getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletService, never()).reverseToHeldForTicket(any(), any(), any());
    }

    // ---------- cấp buổi (câu 34) ----------

    @Test
    void sessionLevel_freezesOnlyOneDayValue() {
        Ticket t = ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);
        session(t, SessionStatus.DONE);

        var response = service.open("customer1", TICKET_ID, SESSION_ID, "PT không đến");

        // payableAmount / dayCount = 1.000.000 / 10
        assertThat(response.getFrozenAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
    }

    /** Buổi chưa diễn ra thì chưa có gì để khiếu nại — khách vẫn đổi lịch/đổi PT được. */
    @Test
    void sessionLevel_scheduledSession_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);
        session(t, SessionStatus.SCHEDULED);

        assertThatThrownBy(() -> service.open("customer1", TICKET_ID, SESSION_ID, "chưa tới"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã diễn ra");
    }

    @Test
    void sessionLevel_sessionOfAnotherTicket_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);
        TrainingSession foreign = TrainingSession.builder()
                .id(SESSION_ID).status(SessionStatus.DONE)
                .ticket(Ticket.builder().id(999L).build())
                .sessionDate(LocalDate.now().minusDays(1)).build();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.open("customer1", TICKET_ID, SESSION_ID, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc vé");
    }

    // ---------- guard chung ----------

    /** P0-0.5: khoá vé trước khi kiểm tra trùng — chống hai luồng cùng đóng băng quỹ. */
    @Test
    void open_locksTicketBeforeDuplicateCheck() {
        ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);

        service.open("customer1", TICKET_ID, null, "lý do");

        verify(ticketRepository).lockById(TICKET_ID);
    }

    @Test
    void open_duplicateUnresolved_isRejected() {
        ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);
        when(disputeRepository.existsByTicket_IdAndStatusIn(eq(TICKET_ID), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.open("customer1", TICKET_ID, null, "lần hai"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được giải quyết");
    }

    @Test
    void open_nonParty_isForbidden() {
        ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);

        assertThatThrownBy(() -> service.open("stranger", TICKET_ID, null, "tò mò"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không phải một bên liên quan");
    }

    @Test
    void open_pendingPaymentTicket_isRejected() {
        ticket(TicketStatus.PENDING_PAYMENT, SettlementStatus.NONE);

        assertThatThrownBy(() -> service.open("customer1", TICKET_ID, null, "chưa trả tiền"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trạng thái PENDING_PAYMENT");
    }

    /**
     * Câu 32 chặn HOÀN TIỀN tự động cho vé hết hạn, nhưng tranh chấp vẫn phải mở
     * được — đó là đường duy nhất còn lại nếu gym thực sự sai.
     */
    @Test
    void open_expiredTicket_isStillAllowed() {
        ticket(TicketStatus.EXPIRED, SettlementStatus.HELD);

        var response = service.open("customer1", TICKET_ID, null, "gym đóng cửa cả tháng");

        assertThat(response.getStatus()).isEqualTo(DisputeStatus.OPEN);
    }

    @Test
    void open_notifiesOtherParties() {
        ticket(TicketStatus.ACTIVE, SettlementStatus.HELD);

        service.open("customer1", TICKET_ID, null, "lý do");

        verify(notificationDispatcher).disputeOpened(any(Dispute.class), eq("customer1"));
    }
}
