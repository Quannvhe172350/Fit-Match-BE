package com.fitmatch.service;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.support.PtSlotValidator;
import com.fitmatch.service.support.SessionSchedulingValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Điều kiện đặt lịch ở cấp vé — không biết gì về PT. */
@ExtendWith(MockitoExtension.class)
class SessionSchedulingValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    private static final Long BRANCH_ID = 7L;

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private OperatingHourRepository operatingHourRepository;
    @InjectMocks private SessionSchedulingValidator validator;

    private Ticket ticket(TicketKind kind, int dayCount, TicketStatus status) {
        return Ticket.builder()
                .id(1L)
                .kind(kind)
                .dayCount(dayCount)
                .status(status)
                .expiresAt(TODAY.plusDays(60).atStartOfDay())
                .gymProfile(GymProfile.builder()
                        .verificationStatus(VerificationStatus.APPROVED).active(true).build())
                .build();
    }

    private void alreadyBooked(long count) {
        when(trainingSessionRepository.countByTicket_IdAndStatusNot(anyLong(), any()))
                .thenReturn(count);
    }

    @Test
    void dayTicket_futureDate_passes() {
        alreadyBooked(0);

        validator.assertSchedulable(ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                List.of(TODAY.plusDays(3)), TODAY);
    }

    /** Vé cả ngày nên đặt cho chính hôm nay là hợp lệ (chỉ mất quyền huỷ). */
    @Test
    void dayTicket_today_passes() {
        alreadyBooked(0);

        validator.assertSchedulable(ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                List.of(TODAY), TODAY);
    }

    @Test
    void pastDate_rejected() {
        alreadyBooked(0);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE), List.of(TODAY.minusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua");
    }

    @Test
    void expiredTicket_rejected() {
        alreadyBooked(0);
        Ticket t = ticket(TicketKind.DAY, 1, TicketStatus.EXPIRED);
        t.setExpiresAt(TODAY.minusDays(1).atStartOfDay());

        assertThatThrownBy(() -> validator.assertSchedulable(t, List.of(TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vé không ở trạng thái sử dụng được");
    }

    /** Ngày tập không được vượt hạn dùng của vé — đặt xong cũng không dùng được. */
    @Test
    void dateBeyondExpiry_rejected() {
        alreadyBooked(0);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE), List.of(TODAY.plusDays(90)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá hạn dùng");
    }

    @Test
    void pendingPaymentTicket_rejected() {
        alreadyBooked(0);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.PENDING_PAYMENT), List.of(TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void bookingMoreDaysThanTicketHas_rejected() {
        alreadyBooked(1);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE), List.of(TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày chưa đặt");
    }

    @Test
    void packageTicket_allDaysAtOnce_passes() {
        alreadyBooked(0);

        validator.assertSchedulable(ticket(TicketKind.PACKAGE, 3, TicketStatus.ACTIVE),
                List.of(TODAY.plusDays(1), TODAY.plusDays(2), TODAY.plusDays(3)), TODAY);
    }

    /** Vé gói phải đặt trọn gói: đặt lẻ từng ngày phá vỡ tính liên tiếp của câu 27. */
    @Test
    void packageTicket_partialBooking_rejected() {
        alreadyBooked(0);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.PACKAGE, 5, TicketStatus.ACTIVE), List.of(TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đặt trọn 5 ngày");
    }

    @Test
    void duplicateDates_rejected() {
        alreadyBooked(0);

        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.PACKAGE, 2, TicketStatus.ACTIVE),
                List.of(TODAY.plusDays(1), TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trùng");
    }

    @Test
    void emptyDates_rejected() {
        assertThatThrownBy(() -> validator.assertSchedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE), List.of(), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất một ngày");
    }

    // --- dời lịch (câu 2/37, nới cho vé gói ở V94) ---

    private TrainingSession session(LocalDate date, SessionStatus status) {
        return TrainingSession.builder().id(5L).sessionDate(date).status(status).build();
    }

    @Test
    void reschedule_dayTicket_futureSession_passes() {
        validator.assertReschedulable(ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY);
    }

    /**
     * V94: vé GÓI cũng dời được một ngày. Luật "n ngày liên tiếp" (câu 27) là luật
     * của lúc ĐẶT; giữ nó mãi về sau nghĩa là người mua gói 10 ngày bận đúng một
     * hôm thì mất trắng hôm đó.
     */
    @Test
    void reschedule_packageTicket_passes() {
        validator.assertReschedulable(ticket(TicketKind.PACKAGE, 10, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY);
    }

    /** Hai ngày của CÙNG một vé rơi vào một ngày là vô nghĩa — vé dùng được cả ngày. */
    @Test
    void reschedule_ontoAnotherDayOfSameTicket_rejected() {
        when(trainingSessionRepository.existsByTicket_IdAndSessionDateAndStatusNotAndIdNot(
                anyLong(), any(), any(), anyLong()))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.assertReschedulable(
                ticket(TicketKind.PACKAGE, 10, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có một ngày tập vào");
    }

    /** Mốc huỷ/đổi là 00:00 ngày tập — hôm nay là ngày tập thì đã khoá. */
    @Test
    void reschedule_sessionIsToday_rejected() {
        assertThatThrownBy(() -> validator.assertReschedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                session(TODAY, SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn đổi lịch");
    }

    @Test
    void reschedule_doneSession_rejected() {
        assertThatThrownBy(() -> validator.assertReschedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                session(TODAY.plusDays(2), SessionStatus.DONE), TODAY.plusDays(5), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không còn ở trạng thái đặt trước");
    }

    @Test
    void reschedule_toPastDate_rejected() {
        assertThatThrownBy(() -> validator.assertReschedulable(
                ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.minusDays(1), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua");
    }

    /**
     * Buổi không có PT phải đi qua ĐÚNG một validator. Ràng buộc này được bảo
     * đảm bằng cấu trúc chứ không bằng nhánh if: lớp này không hề phụ thuộc
     * {@link PtSlotValidator}, nên không có đường nào gọi nhầm.
     */
    @Test
    void doesNotDependOnPtSlotValidator() {
        assertThat(SessionSchedulingValidator.class.getDeclaredFields())
                .noneMatch(f -> f.getType() == PtSlotValidator.class);
    }

    @Test
    void ticketExpiresAtInThePast_flaggedEvenWhenStatusStillActive() {
        alreadyBooked(0);
        Ticket t = ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE);
        t.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> validator.assertSchedulable(t, List.of(TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vé đã hết hạn");
    }

    // ---------- giờ đóng cửa của chi nhánh (chỉ áp cho ngày HÔM NAY) ----------

    /** Vé gắn chi nhánh — các test cũ để null nên không chạm luật giờ đóng cửa. */
    private Ticket ticketAtBranch() {
        Ticket t = ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE);
        t.setGymBranch(GymBranch.builder().id(BRANCH_ID).name("Chi nhánh 1").build());
        return t;
    }

    private void openingHours(LocalTime open, LocalTime close, boolean closed) {
        when(operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(BRANCH_ID))
                .thenReturn(List.of(OperatingHour.builder()
                        .dayOfWeek(TODAY.getDayOfWeek().getValue())
                        .openTime(open).closeTime(close).closed(closed).build()));
    }

    @Test
    void today_beforeClosingTime_passes() {
        alreadyBooked(0);
        openingHours(LocalTime.of(6, 0), LocalTime.of(22, 0), false);

        validator.assertSchedulable(ticketAtBranch(), List.of(TODAY),
                TODAY.atTime(21, 59));
    }

    @Test
    void today_afterClosingTime_rejected() {
        alreadyBooked(0);
        openingHours(LocalTime.of(6, 0), LocalTime.of(22, 0), false);

        assertThatThrownBy(() -> validator.assertSchedulable(ticketAtBranch(), List.of(TODAY),
                TODAY.atTime(22, 30)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng cửa hôm nay");
    }

    /** Đúng giờ đóng cửa cũng là muộn: 22:00 nghĩa là 22:00 đã ngừng nhận. */
    @Test
    void today_exactlyAtClosingTime_rejected() {
        alreadyBooked(0);
        openingHours(LocalTime.of(6, 0), LocalTime.of(22, 0), false);

        assertThatThrownBy(() -> validator.assertSchedulable(ticketAtBranch(), List.of(TODAY),
                TODAY.atTime(22, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng cửa hôm nay");
    }

    /** Ngày mai thì giờ giấc hôm nay không nói lên điều gì — gym còn cả ngày để mở. */
    @Test
    void tomorrow_afterTodaysClosingTime_passes() {
        alreadyBooked(0);

        validator.assertSchedulable(ticketAtBranch(), List.of(TODAY.plusDays(1)),
                TODAY.atTime(23, 30));
    }

    @Test
    void today_branchClosedAllDay_rejected() {
        alreadyBooked(0);
        openingHours(null, null, true);

        assertThatThrownBy(() -> validator.assertSchedulable(ticketAtBranch(), List.of(TODAY),
                TODAY.atTime(9, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nghỉ hôm nay");
    }

    /** Đã khai giờ nhưng thiếu đúng thứ trong tuần đó = nghỉ. */
    @Test
    void today_weekdayMissingFromDeclaredHours_rejected() {
        alreadyBooked(0);
        when(operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(BRANCH_ID))
                .thenReturn(List.of(OperatingHour.builder()
                        .dayOfWeek(TODAY.plusDays(1).getDayOfWeek().getValue())
                        .openTime(LocalTime.of(6, 0)).closeTime(LocalTime.of(22, 0)).build()));

        assertThatThrownBy(() -> validator.assertSchedulable(ticketAtBranch(), List.of(TODAY),
                TODAY.atTime(9, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nghỉ hôm nay");
    }

    /**
     * Chi nhánh chưa khai giờ thì KHÔNG suy ra là đóng cửa — nếu không, mọi chi
     * nhánh chưa kịp cấu hình sẽ âm thầm mất quyền đặt lịch trong ngày.
     */
    @Test
    void today_branchWithoutDeclaredHours_passes() {
        alreadyBooked(0);
        when(operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(BRANCH_ID))
                .thenReturn(List.of());

        validator.assertSchedulable(ticketAtBranch(), List.of(TODAY), TODAY.atTime(23, 0));
    }

    /** Dời sang hôm nay cũng phải trước giờ đóng cửa — cùng luật với đặt mới. */
    @Test
    void reschedule_toTodayAfterClosingTime_rejected() {
        openingHours(LocalTime.of(6, 0), LocalTime.of(22, 0), false);
        Ticket t = ticketAtBranch();
        TrainingSession session = TrainingSession.builder()
                .id(5L).ticket(t).status(SessionStatus.SCHEDULED)
                .sessionDate(TODAY.plusDays(3)).build();

        assertThatThrownBy(() -> validator.assertReschedulable(t, session, TODAY,
                TODAY.atTime(22, 30)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng cửa hôm nay");
    }
}
