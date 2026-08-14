package com.fitmatch.service;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.repository.PtAvailabilityRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.impl.TicketMaintenanceServiceImpl;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.TicketLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ba job nền: hoàn tất buổi theo ngày, hết hạn vé, cảnh báo lịch PT mỏng. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketMaintenanceServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TrainingSessionRepository sessionRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtAvailabilityRepository ptAvailabilityRepository;
    @Mock private TicketLifecycle ticketLifecycle;
    @Mock private SessionLifecycle sessionLifecycle;
    @Mock private SettlementService settlementService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private TicketMaintenanceServiceImpl service;

    private Ticket ticket(TicketStatus status, int dayCount) {
        return Ticket.builder()
                .id(100L)
                .customer(User.builder().id(9L).username("customer1").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 3 ngày").build())
                .gymProfile(GymProfile.builder().id(1L).gymName("Gym A").build())
                .kind(TicketKind.PACKAGE).dayCount(dayCount)
                .status(status)
                .settlementStatus(SettlementStatus.HELD)
                .payableAmount(BigDecimal.valueOf(300_000))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    private TrainingSession session(Ticket t, long id, SessionStatus status) {
        return TrainingSession.builder()
                .id(id).ticket(t).status(status)
                .sessionDate(LocalDate.now().minusDays(1)).dayIndex((int) id)
                .build();
    }

    // ---------- hoàn tất buổi theo ngày ----------

    /** Câu 9: ngày đã qua là DONE, bất kể khách có check-in hay không. */
    @Test
    void completeElapsedSessions_movesPastSessionsToDone() {
        Ticket t = ticket(TicketStatus.ACTIVE, 3);
        TrainingSession s = session(t, 1L, SessionStatus.SCHEDULED);
        s.setCheckedInAt(null);
        when(sessionRepository.findByStatusAndSessionDateBefore(eq(SessionStatus.SCHEDULED), any()))
                .thenReturn(List.of(s));
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(100L)).thenReturn(List.of(s));

        int completed = service.completeElapsedSessions();

        assertThat(completed).isEqualTo(1);
        verify(sessionLifecycle).transition(eq(s), eq(SessionStatus.DONE), any());
    }

    /** Vé còn ngày chưa đặt thì CHƯA dùng hết — khách vẫn còn quyền tới hạn vé. */
    @Test
    void completeElapsedSessions_ticketWithUnbookedDays_staysActive() {
        Ticket t = ticket(TicketStatus.ACTIVE, 3);
        TrainingSession s = session(t, 1L, SessionStatus.DONE);
        when(sessionRepository.findByStatusAndSessionDateBefore(eq(SessionStatus.SCHEDULED), any()))
                .thenReturn(List.of(s));
        // Chỉ 1/3 ngày được đặt.
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(100L)).thenReturn(List.of(s));

        service.completeElapsedSessions();

        verify(ticketLifecycle, never()).transition(any(), eq(TicketStatus.USED_UP), any());
        verify(settlementService, never()).settleTicketAfterFulfillment(any(), any());
    }

    /** Buổi cuối xong -> vé USED_UP -> giải ngân (câu 15). */
    @Test
    void completeElapsedSessions_lastDay_marksUsedUpAndSettles() {
        Ticket t = ticket(TicketStatus.ACTIVE, 2);
        TrainingSession done = session(t, 1L, SessionStatus.DONE);
        TrainingSession last = session(t, 2L, SessionStatus.SCHEDULED);
        when(sessionRepository.findByStatusAndSessionDateBefore(eq(SessionStatus.SCHEDULED), any()))
                .thenReturn(List.of(last));
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(100L)).thenAnswer(inv -> {
            last.setStatus(SessionStatus.DONE); // lifecycle bị mock nên tự cập nhật
            return List.of(done, last);
        });

        service.completeElapsedSessions();

        verify(ticketLifecycle).transition(eq(t), eq(TicketStatus.USED_UP), any());
        verify(settlementService).settleTicketAfterFulfillment(eq(t), any());
        verify(notificationDispatcher).ticketUsedUp(t);
    }

    /** Buổi bị huỷ không tính vào số ngày phải hoàn tất. */
    @Test
    void completeElapsedSessions_cancelledSessionsDoNotBlockUsedUp() {
        Ticket t = ticket(TicketStatus.ACTIVE, 1);
        TrainingSession cancelled = session(t, 1L, SessionStatus.CANCELLED);
        TrainingSession last = session(t, 2L, SessionStatus.SCHEDULED);
        when(sessionRepository.findByStatusAndSessionDateBefore(eq(SessionStatus.SCHEDULED), any()))
                .thenReturn(List.of(last));
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(100L)).thenAnswer(inv -> {
            last.setStatus(SessionStatus.DONE);
            return List.of(cancelled, last);
        });

        service.completeElapsedSessions();

        verify(ticketLifecycle).transition(eq(t), eq(TicketStatus.USED_UP), any());
    }

    @Test
    void completeElapsedSessions_nothingElapsed_doesNothing() {
        when(sessionRepository.findByStatusAndSessionDateBefore(any(), any())).thenReturn(List.of());

        assertThat(service.completeElapsedSessions()).isZero();
        verify(sessionLifecycle, never()).transition(any(), any(), any());
    }

    // ---------- hết hạn vé ----------

    /** Câu 32: quá hạn -> EXPIRED và tiền tự về gym. */
    @Test
    void expireOverdueTickets_marksExpiredAndSettles() {
        Ticket t = ticket(TicketStatus.ACTIVE, 3);
        t.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(ticketRepository.findByStatusAndExpiresAtBefore(eq(TicketStatus.ACTIVE), any()))
                .thenReturn(List.of(t));

        int expired = service.expireOverdueTickets();

        assertThat(expired).isEqualTo(1);
        verify(ticketLifecycle).transition(eq(t), eq(TicketStatus.EXPIRED), any());
        verify(settlementService).settleTicketAfterFulfillment(eq(t), any());
        verify(notificationDispatcher).ticketExpired(t);
    }

    /**
     * Idempotent: lần chạy thứ hai không còn vé ACTIVE nào quá hạn nên không
     * giải ngân lần nữa — chốt chặn thật nằm ở settlementStatus.
     */
    @Test
    void expireOverdueTickets_secondRun_findsNothing() {
        when(ticketRepository.findByStatusAndExpiresAtBefore(eq(TicketStatus.ACTIVE), any()))
                .thenReturn(List.of());

        assertThat(service.expireOverdueTickets()).isZero();
        verify(settlementService, never()).settleTicketAfterFulfillment(any(), any());
    }

    @Test
    void notifyExpiringSoon_warnsCustomers() {
        Ticket t = ticket(TicketStatus.ACTIVE, 3);
        when(ticketRepository.findByStatusAndExpiresAtBetween(eq(TicketStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(t));

        assertThat(service.notifyExpiringSoon()).isEqualTo(1);
        verify(notificationDispatcher).ticketExpiringSoon(eq(t), anyLong());
    }

    // ---------- cảnh báo lịch PT mỏng (quyết định #8) ----------

    private PtProfile pt() {
        return PtProfile.builder()
                .id(11L).displayName("PT A").status(PtStatus.ACTIVE)
                .user(User.builder().id(5L).username("pt1").build())
                .gymProfile(GymProfile.builder().id(1L)
                        .user(User.builder().id(2L).username("gym1").build()).build())
                .build();
    }

    @Test
    void warnPts_below20Days_warnsButNeverBlocks() {
        PtProfile pt = pt();
        when(ptProfileRepository.findByStatus(PtStatus.ACTIVE)).thenReturn(List.of(pt));
        when(ptAvailabilityRepository.countDistinctDaysFrom(eq(11L), any())).thenReturn(19L);

        assertThat(service.warnPtsWithThinAvailability()).isEqualTo(1);
        verify(notificationDispatcher).ptAvailabilityBelowThreshold(any(), any(), eq(19L), eq(20));
        // Không có nhánh nào đổi trạng thái PT — PT vẫn ACTIVE và vẫn đặt được.
        assertThat(pt.getStatus()).isEqualTo(PtStatus.ACTIVE);
    }

    @Test
    void warnPts_atThreshold_noWarning() {
        when(ptProfileRepository.findByStatus(PtStatus.ACTIVE)).thenReturn(List.of(pt()));
        when(ptAvailabilityRepository.countDistinctDaysFrom(eq(11L), any())).thenReturn(20L);

        assertThat(service.warnPtsWithThinAvailability()).isZero();
        verify(notificationDispatcher, never())
                .ptAvailabilityBelowThreshold(any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }
}
