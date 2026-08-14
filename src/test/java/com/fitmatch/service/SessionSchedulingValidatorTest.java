package com.fitmatch.service;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
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

    @Mock private TrainingSessionRepository trainingSessionRepository;
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

    // --- dời lịch (câu 2/37) ---

    private TrainingSession session(LocalDate date, SessionStatus status) {
        return TrainingSession.builder().id(5L).sessionDate(date).status(status).build();
    }

    @Test
    void reschedule_dayTicket_futureSession_passes() {
        validator.assertReschedulable(ticket(TicketKind.DAY, 1, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY);
    }

    @Test
    void reschedule_packageTicket_rejected() {
        assertThatThrownBy(() -> validator.assertReschedulable(
                ticket(TicketKind.PACKAGE, 10, TicketStatus.ACTIVE),
                session(TODAY.plusDays(3), SessionStatus.SCHEDULED), TODAY.plusDays(5), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vé gói không đổi được lịch");
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
}
