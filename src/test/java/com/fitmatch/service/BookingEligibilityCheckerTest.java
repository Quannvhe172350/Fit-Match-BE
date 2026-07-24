package com.fitmatch.service;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.service.support.BookingEligibilityChecker;
import com.fitmatch.service.support.ScheduleConflictValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2-2.10: enforcement phải chặn end <= start ngay cả khi booking không có PT/chi nhánh. */
@ExtendWith(MockitoExtension.class)
class BookingEligibilityCheckerTest {

    @Mock private ScheduleConflictValidator scheduleConflictValidator;
    @Mock private BookingRepository bookingRepository;
    @Mock private PtAssignmentRepository ptAssignmentRepository;
    @InjectMocks private BookingEligibilityChecker checker;

    private Booking serviceOnlyBooking(LocalDateTime start, LocalDateTime end) {
        return Booking.builder()
                .id(1L)
                .gymProfile(GymProfile.builder()
                        .verificationStatus(VerificationStatus.APPROVED).active(true).build())
                .gymService(GymService.builder().status(CatalogStatus.PUBLISHED).build())
                .customer(User.builder().id(9L).build())
                .startAt(start).endAt(end)
                .build();
    }

    @Test
    void assertEligible_endBeforeStart_serviceOnly_rejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Booking b = serviceOnlyBooking(start, start.minusHours(1)); // end < start

        assertThatThrownBy(() -> checker.assertEligible(b))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ kết thúc phải sau giờ bắt đầu");
    }

    @Test
    void assertEligible_validServiceOnly_passes() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Booking b = serviceOnlyBooking(start, start.plusHours(1));

        // Không có PT/chi nhánh, customer không trùng lịch (mock đếm = 0) -> hợp lệ.
        checker.assertEligible(b);
    }
}
