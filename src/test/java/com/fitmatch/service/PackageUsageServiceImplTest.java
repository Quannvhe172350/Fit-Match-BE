package com.fitmatch.service;

import com.fitmatch.common.enums.CustomerPackageStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.CustomerPackage;
import com.fitmatch.entity.TrainingPackage;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.CustomerPackageRepository;
import com.fitmatch.service.impl.PackageUsageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageUsageServiceImplTest {

    @Mock private CustomerPackageRepository customerPackageRepository;
    @InjectMocks private PackageUsageServiceImpl service;

    private TrainingPackage pkg(int sessions, Integer validityDays) {
        return TrainingPackage.builder().id(3L).sessionCount(sessions).validityDays(validityDays).build();
    }

    @Test
    void purchaseBookingCompleted_activatesPackage_firstSessionConsumed() {
        Booking purchase = Booking.builder().id(10L)
                .customer(User.builder().username("john").build())
                .trainingPackage(pkg(10, 90))
                .build();
        when(customerPackageRepository.existsByPurchaseBooking_Id(10L)).thenReturn(false);
        when(customerPackageRepository.save(any(CustomerPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onBookingFulfilled(purchase);

        ArgumentCaptor<CustomerPackage> captor = ArgumentCaptor.forClass(CustomerPackage.class);
        verify(customerPackageRepository).save(captor.capture());
        CustomerPackage cp = captor.getValue();
        assertThat(cp.getSessionsTotal()).isEqualTo(10);
        assertThat(cp.getSessionsUsed()).isEqualTo(1);
        assertThat(cp.getStatus()).isEqualTo(CustomerPackageStatus.ACTIVE);
        assertThat(cp.getExpiresAt()).isNotNull();
    }

    @Test
    void sessionBookingCompleted_consumesAndExhaustsAtTotal() {
        CustomerPackage cp = CustomerPackage.builder()
                .id(1L).sessionsTotal(2).sessionsUsed(1)
                .status(CustomerPackageStatus.ACTIVE)
                .build();
        Booking session = Booking.builder().id(11L).customerPackage(cp)
                .trainingPackage(pkg(2, null)).build();
        when(customerPackageRepository.save(any(CustomerPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onBookingFulfilled(session);

        assertThat(cp.getSessionsUsed()).isEqualTo(2);
        assertThat(cp.getStatus()).isEqualTo(CustomerPackageStatus.EXHAUSTED);
    }

    @Test
    void serviceBooking_noPackage_noop() {
        Booking booking = Booking.builder().id(12L).build();

        service.onBookingFulfilled(booking);

        verify(customerPackageRepository, never()).save(any());
    }

    @Test
    void requireUsable_expiredPackage_throwsAndMarksExpired() {
        CustomerPackage cp = CustomerPackage.builder()
                .id(1L).sessionsTotal(10).sessionsUsed(2)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .status(CustomerPackageStatus.ACTIVE)
                .build();
        when(customerPackageRepository.findByIdAndCustomer_Username(1L, "john"))
                .thenReturn(Optional.of(cp));
        when(customerPackageRepository.save(any(CustomerPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.requireUsable(1L, "john"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(cp.getStatus()).isEqualTo(CustomerPackageStatus.EXPIRED);
    }
}
