package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.booking.CorrectAttendanceRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.support.AttendanceSupport;
import com.fitmatch.service.support.BookingLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttendanceSupportTest {

    @Mock private BookingLifecycle bookingLifecycle;
    @Mock private AuditService auditService;
    @InjectMocks private AttendanceSupport support;

    private Booking confirmed(LocalDateTime start, LocalDateTime end) {
        return Booking.builder().id(1L)
                .status(BookingStatus.CONFIRMED)
                .startAt(start).endAt(end)
                .settlementStatus(SettlementStatus.HELD)
                .build();
    }

    @Test
    void checkIn_withinWindow_setsTimestamp() {
        Booking b = confirmed(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));

        support.checkIn(b, "customer john");

        assertThat(b.getCheckedInAt()).isNotNull();
        verify(bookingLifecycle).recordNote(eq(b), anyString());
    }

    @Test
    void checkIn_tooEarly_rejected() {
        Booking b = confirmed(LocalDateTime.now().plusHours(2), LocalDateTime.now().plusHours(3));

        assertThatThrownBy(() -> support.checkIn(b, "customer john"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void checkIn_twice_rejected() {
        Booking b = confirmed(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));
        b.setCheckedInAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> support.checkIn(b, "gym g1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void checkIn_wrongStatus_rejected() {
        Booking b = confirmed(LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        b.setStatus(BookingStatus.PENDING_GYM);

        assertThatThrownBy(() -> support.checkIn(b, "customer john"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void correct_afterFundsReleased_rejected() {
        Booking b = confirmed(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        b.setStatus(BookingStatus.COMPLETED);
        b.setSettlementStatus(SettlementStatus.RELEASED);

        assertThatThrownBy(() -> support.correct(b,
                new CorrectAttendanceRequest(null, null, BookingStatus.NO_SHOW, "typo"), "admin a"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void correct_statusFlip_delegatesToLifecycleOverride() {
        Booking b = confirmed(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        b.setStatus(BookingStatus.NO_SHOW);
        b.setSettlementStatus(SettlementStatus.PENDING_RELEASE);

        support.correct(b,
                new CorrectAttendanceRequest(null, null, BookingStatus.COMPLETED, "customer proved attendance"),
                "gym g1");

        verify(bookingLifecycle).overrideForCorrection(eq(b), eq(BookingStatus.COMPLETED), anyString());
    }

    @Test
    void correct_nothingProvided_validationError() {
        Booking b = confirmed(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        b.setStatus(BookingStatus.COMPLETED);

        assertThatThrownBy(() -> support.correct(b,
                new CorrectAttendanceRequest(null, null, null, "reason"), "gym g1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
