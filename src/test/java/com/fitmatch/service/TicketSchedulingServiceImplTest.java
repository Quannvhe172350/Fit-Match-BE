package com.fitmatch.service;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.ticket.ScheduleTicketRequest;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.UpdateSessionPtRequest;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtAvailability;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.impl.TicketSchedulingServiceImpl;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PackageDayGenerator;
import com.fitmatch.service.support.PtSlotValidator;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.SessionSchedulingValidator;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Đặt lịch, dời ngày, gán PT và check-in. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketSchedulingServiceImplTest {

    private static final String USERNAME = "customer1";
    private static final Long TICKET_ID = 100L;
    private static final Long PT_ID = 11L;
    private static final LocalTime SLOT = LocalTime.of(18, 0);

    @Mock private TicketRepository ticketRepository;
    @Mock private TrainingSessionRepository sessionRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private SessionSchedulingValidator schedulingValidator;
    @Mock private PtSlotValidator ptSlotValidator;
    @Spy private PackageDayGenerator packageDayGenerator = new PackageDayGenerator();
    @Mock private SessionLifecycle sessionLifecycle;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private TicketSchedulingServiceImpl service;

    private GymBranch branch;

    @BeforeEach
    void setUp() {
        GymProfile gym = GymProfile.builder()
                .id(1L).gymName("Gym A")
                .verificationStatus(VerificationStatus.APPROVED).active(true)
                .user(User.builder().id(2L).username("gym1").build())
                .build();
        branch = GymBranch.builder().id(22L).name("Chi nhánh 1").gymProfile(gym).build();

        when(sessionRepository.countByTicket_IdAndStatusNot(anyLong(), any())).thenReturn(0L);
        when(sessionRepository.save(any(TrainingSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.findByTicket_IdOrderByDayIndexAsc(TICKET_ID)).thenReturn(List.of());
        when(ptProfileRepository.findById(PT_ID)).thenReturn(Optional.of(
                PtProfile.builder().id(PT_ID).displayName("PT A").build()));
        when(ptSlotValidator.resolveSlot(any(), any(), any(), any(), any())).thenReturn(
                PtAvailability.builder().startTime(SLOT).endTime(LocalTime.of(19, 0)).build());
    }

    private Ticket ticket(TicketKind kind, int dayCount, boolean withPt) {
        Ticket t = Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username(USERNAME).fullName("Khách A").build())
                .ticketType(TicketType.builder().id(33L).name("Vé").build())
                .gymProfile(branch.getGymProfile())
                .gymBranch(branch)
                .kind(kind).dayCount(dayCount).withPt(withPt)
                .status(TicketStatus.ACTIVE)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .build();
        when(ticketRepository.findByIdAndCustomer_Username(TICKET_ID, USERNAME))
                .thenReturn(Optional.of(t));
        return t;
    }

    private TrainingSession session(Ticket t, LocalDate date, PtProfile pt) {
        TrainingSession s = TrainingSession.builder()
                .id(500L).ticket(t).gymBranch(branch)
                .dayIndex(1).sessionDate(date).status(SessionStatus.SCHEDULED)
                .ptProfile(pt).ptSlotStart(pt != null ? SLOT : null)
                .build();
        when(sessionRepository.findByIdAndTicket_Customer_Username(500L, USERNAME))
                .thenReturn(Optional.of(s));
        return s;
    }

    // ---------- đặt lịch ----------

    @Test
    void schedule_packageTicket_generatesConsecutiveDays() {
        Ticket t = ticket(TicketKind.PACKAGE, 5, false);
        LocalDate start = LocalDate.now().plusDays(3);

        service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder().startDate(start).build());

        ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
        verify(sessionRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TrainingSession::getSessionDate)
                .containsExactly(start, start.plusDays(1), start.plusDays(2),
                        start.plusDays(3), start.plusDays(4));
        assertThat(captor.getAllValues()).extracting(TrainingSession::getDayIndex)
                .containsExactly(1, 2, 3, 4, 5);
    }

    /** startDate của vé là mốc tính hoàn tiền một phần — luôn là ngày sớm nhất. */
    @Test
    void schedule_setsTicketStartDateToEarliestDay() {
        Ticket t = ticket(TicketKind.PACKAGE, 3, false);
        LocalDate start = LocalDate.now().plusDays(2);

        service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder().startDate(start).build());

        assertThat(t.getStartDate()).isEqualTo(start);
    }

    /** Câu 8: vé gói có PT được để trống PT ở vài ngày, bổ sung sau. */
    @Test
    void schedule_packageWithPt_allowsDaysWithoutPt() {
        ticket(TicketKind.PACKAGE, 3, true);
        LocalDate start = LocalDate.now().plusDays(1);

        service.schedule(USERNAME, TICKET_ID, ScheduleTicketRequest.builder()
                .startDate(start)
                .days(List.of(ScheduleTicketRequest.DayPt.builder()
                        .dayIndex(1).ptId(PT_ID).slotStart(SLOT).build()))
                .build());

        ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
        verify(sessionRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getPtProfile()).isNotNull();
        assertThat(captor.getAllValues().get(1).getPtProfile()).isNull();
        assertThat(captor.getAllValues().get(2).getPtProfile()).isNull();
    }

    /** Vé không kèm PT thì không được gán PT — khách chưa trả phụ phí. */
    @Test
    void schedule_ptOnNonPtTicket_isRejected() {
        ticket(TicketKind.DAY, 1, false);

        assertThatThrownBy(() -> service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder()
                        .date(LocalDate.now().plusDays(1)).ptId(PT_ID).slotStart(SLOT).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không kèm PT");
    }

    @Test
    void schedule_dayTicketWithoutDate_isRejected() {
        ticket(TicketKind.DAY, 1, false);

        assertThatThrownBy(() -> service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("date is required");
    }

    /** Buổi không PT không được chạm PtSlotValidator. */
    @Test
    void schedule_withoutPt_neverCallsPtSlotValidator() {
        ticket(TicketKind.DAY, 1, false);

        service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder().date(LocalDate.now().plusDays(1)).build());

        verify(ptSlotValidator, never()).resolveSlot(any(), any(), any(), any(), any());
    }

    @Test
    void schedule_notifiesGymForEachDay() {
        ticket(TicketKind.PACKAGE, 4, false);

        service.schedule(USERNAME, TICKET_ID,
                ScheduleTicketRequest.builder().startDate(LocalDate.now().plusDays(1)).build());

        verify(notificationDispatcher, org.mockito.Mockito.times(4)).sessionBooked(any());
    }

    // ---------- dời ngày ----------

    @Test
    void updateDate_movesSessionAndRecordsHistory() {
        Ticket t = ticket(TicketKind.DAY, 1, false);
        TrainingSession s = session(t, LocalDate.now().plusDays(3), null);
        LocalDate newDate = LocalDate.now().plusDays(6);

        service.updateDate(USERNAME, 500L, newDate);

        assertThat(s.getSessionDate()).isEqualTo(newDate);
        verify(sessionLifecycle).recordNote(any(), org.mockito.ArgumentMatchers.contains("Dời lịch"));
        verify(notificationDispatcher).sessionRescheduled(any(), any());
    }

    /** Dời sang ngày mới phải kiểm lại khung PT — PT có thể không rảnh ngày đó. */
    @Test
    void updateDate_withPt_revalidatesSlotOnNewDate() {
        Ticket t = ticket(TicketKind.DAY, 1, true);
        TrainingSession s = session(t, LocalDate.now().plusDays(3),
                PtProfile.builder().id(PT_ID).displayName("PT A").build());
        LocalDate newDate = LocalDate.now().plusDays(6);

        service.updateDate(USERNAME, 500L, newDate);

        verify(ptSlotValidator).resolveSlot(PT_ID, branch.getId(), newDate, SLOT, 500L);
        assertThat(s.getPtSlotEnd()).isEqualTo(LocalTime.of(19, 0));
    }

    // ---------- PT ----------

    @Test
    void setPt_assignsSlotEndFromDeclaredAvailability() {
        Ticket t = ticket(TicketKind.PACKAGE, 5, true);
        TrainingSession s = session(t, LocalDate.now().plusDays(3), null);

        service.setPt(USERNAME, 500L,
                UpdateSessionPtRequest.builder().ptId(PT_ID).slotStart(SLOT).build());

        assertThat(s.getPtProfile()).isNotNull();
        assertThat(s.getPtSlotStart()).isEqualTo(SLOT);
        assertThat(s.getPtSlotEnd()).isEqualTo(LocalTime.of(19, 0));
        verify(notificationDispatcher).sessionPtAssigned(s);
    }

    @Test
    void setPt_onPastSession_isRejected() {
        Ticket t = ticket(TicketKind.PACKAGE, 5, true);
        session(t, LocalDate.now(), null);

        assertThatThrownBy(() -> service.setPt(USERNAME, 500L,
                UpdateSessionPtRequest.builder().ptId(PT_ID).slotStart(SLOT).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn chỉnh sửa");
    }

    /** Bỏ PT không sinh hoàn tiền — phụ phí tính theo vé chứ không theo ngày. */
    @Test
    void removePt_clearsSlotWithoutRefund() {
        Ticket t = ticket(TicketKind.PACKAGE, 5, true);
        TrainingSession s = session(t, LocalDate.now().plusDays(3),
                PtProfile.builder().id(PT_ID).displayName("PT A").build());

        service.removePt(USERNAME, 500L);

        assertThat(s.getPtProfile()).isNull();
        assertThat(s.getPtSlotStart()).isNull();
        assertThat(s.getPtSlotEnd()).isNull();
        assertThat(t.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    // ---------- check-in ----------

    @Test
    void checkIn_sessionWithoutPt_isRejected() {
        Ticket t = ticket(TicketKind.DAY, 1, false);
        session(t, LocalDate.now(), null);

        assertThatThrownBy(() -> service.checkIn(USERNAME, 500L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("có PT");
    }

    @Test
    void checkIn_onTrainingDay_setsTimestampWithoutChangingStatus() {
        Ticket t = ticket(TicketKind.DAY, 1, true);
        TrainingSession s = session(t, LocalDate.now(),
                PtProfile.builder().id(PT_ID).displayName("PT A").build());

        service.checkIn(USERNAME, 500L);

        assertThat(s.getCheckedInAt()).isNotNull();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        verify(sessionLifecycle).recordNote(any(), org.mockito.ArgumentMatchers.contains("check-in"));
    }

    @Test
    void checkIn_outsideTrainingDay_isRejected() {
        Ticket t = ticket(TicketKind.DAY, 1, true);
        session(t, LocalDate.now().plusDays(2),
                PtProfile.builder().id(PT_ID).displayName("PT A").build());

        assertThatThrownBy(() -> service.checkIn(USERNAME, 500L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trong ngày tập");
    }

    @Test
    void checkIn_twice_isRejected() {
        Ticket t = ticket(TicketKind.DAY, 1, true);
        TrainingSession s = session(t, LocalDate.now(),
                PtProfile.builder().id(PT_ID).displayName("PT A").build());
        s.setCheckedInAt(java.time.LocalDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> service.checkIn(USERNAME, 500L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được check-in");
    }
}
