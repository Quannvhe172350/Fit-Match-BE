package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.support.BookingAutoNoShowService;
import com.fitmatch.service.support.BookingLifecycle;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-043 (BE-3): auto no-show — booking CONFIRMED quá endAt + grace, chưa check-in
 * bị hệ thống đánh NO_SHOW với đầy đủ hiệu ứng tài chính; đã check-in thì không đụng.
 */
@ExtendWith(MockitoExtension.class)
class BookingAutoNoShowServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingLifecycle bookingLifecycle;
    @Mock private SettlementService settlementService;
    @Mock private PackageUsageService packageUsageService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private AuditService auditService;
    @InjectMocks private BookingAutoNoShowService service;

    private Booking confirmed(LocalDateTime endAt, LocalDateTime checkedInAt) {
        return Booking.builder()
                .id(1L)
                .status(BookingStatus.CONFIRMED)
                .startAt(endAt.minusHours(1))
                .endAt(endAt)
                .checkedInAt(checkedInAt)
                .build();
    }

    @Test
    void markOneNoShow_overdueWithoutCheckIn_appliesFullNoShowEffects() {
        ReflectionTestUtils.setField(service, "graceHours", 24L);
        Booking b = confirmed(LocalDateTime.now().minusHours(30), null);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        service.markOneNoShow(1L);

        verify(bookingLifecycle).transition(eq(b), eq(BookingStatus.NO_SHOW), any());
        verify(settlementService).settleAfterFulfillment(eq(b), any());
        verify(packageUsageService).onBookingFulfilled(b);
        verify(notificationDispatcher).bookingNoShow(b);
        verify(auditService).record(any(), eq("Booking"), eq(1L), any());
    }

    @Test
    void markOneNoShow_checkedIn_skips() {
        // Đối xứng P1-24: khách đã check-in thì không auto no-show — gym phải complete.
        ReflectionTestUtils.setField(service, "graceHours", 24L);
        Booking b = confirmed(LocalDateTime.now().minusHours(30), LocalDateTime.now().minusHours(31));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        service.markOneNoShow(1L);

        verifyNoInteractions(bookingLifecycle, settlementService, packageUsageService);
    }

    @Test
    void markOneNoShow_notYetPastGrace_skips() {
        ReflectionTestUtils.setField(service, "graceHours", 24L);
        Booking b = confirmed(LocalDateTime.now().minusHours(2), null);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        service.markOneNoShow(1L);

        verifyNoInteractions(bookingLifecycle, settlementService);
    }

    @Test
    void markOneNoShow_alreadyHandledByGym_skips() {
        ReflectionTestUtils.setField(service, "graceHours", 24L);
        Booking b = confirmed(LocalDateTime.now().minusHours(30), null);
        b.setStatus(BookingStatus.NO_SHOW);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        service.markOneNoShow(1L);

        verifyNoInteractions(bookingLifecycle, settlementService);
    }
}
