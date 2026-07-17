package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Booking;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.impl.GymBookingServiceImpl;
import com.fitmatch.service.support.AttendanceSupport;
import com.fitmatch.service.support.BookingEligibilityChecker;
import com.fitmatch.service.support.BookingLifecycle;
import com.fitmatch.service.support.BookingPromotionRefunder;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-047/049: không cho complete booking khách chưa check-in. */
@ExtendWith(MockitoExtension.class)
class GymBookingCompleteGuardTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private BookingEligibilityChecker bookingEligibilityChecker;
    @Mock private BookingLifecycle bookingLifecycle;
    @Mock private AuditService auditService;
    @Mock private RefundService refundService;
    @Mock private SettlementService settlementService;
    @Mock private PaymentService paymentService;
    @Mock private PackageUsageService packageUsageService;
    @Mock private AttendanceSupport attendanceSupport;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private LoyaltyService loyaltyService;
    @Mock private BookingPromotionRefunder promotionRefunder;
    @InjectMocks private GymBookingServiceImpl service;

    @Test
    void complete_withoutCheckIn_throwsAndDoesNotTransition() {
        Booking b = Booking.builder().id(1L).status(BookingStatus.CONFIRMED)
                .startAt(LocalDateTime.now().minusHours(1))
                .build(); // checkedInAt == null
        when(bookingRepository.findByIdAndGymProfile_User_Username(1L, "gym"))
                .thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.complete("gym", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);

        verify(bookingLifecycle, never()).transition(any(), any(), any());
        verify(settlementService, never()).settleAfterFulfillment(any(), any());
    }
}
