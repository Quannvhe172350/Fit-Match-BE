package com.fitmatch.service;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.impl.AdminPtManagementServiceImpl;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-021 / P0-3 (audit 2026-07-17): admin đình chỉ PT phải resolve theo User.id
 * (caller là trang quản lý user) — trước đây tra PtProfile.id khiến đình chỉ nhầm
 * PT khác vì hai dãy id auto-increment độc lập.
 */
@ExtendWith(MockitoExtension.class)
class AdminPtManagementServiceImplTest {

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private AuditService auditService;
    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private AdminPtManagementServiceImpl service;

    private PtProfile ptProfile(long profileId, long userId, PtStatus status) {
        return PtProfile.builder()
                .id(profileId)
                .user(User.builder().id(userId).username("pt" + userId).build())
                .status(status)
                .active(status == PtStatus.ACTIVE)
                .build();
    }

    @Test
    void suspend_resolvesByUserId_notProfileId() {
        // profileId (7) khác userId (42) — nếu code tra findById(42) sẽ đánh nhầm người
        PtProfile profile = ptProfile(7L, 42L, PtStatus.ACTIVE);
        when(ptProfileRepository.findByUser_Id(42L)).thenReturn(Optional.of(profile));
        when(bookingRepository.findByPtProfile_IdAndStatusInAndStartAtGreaterThan(
                eq(7L), any(), any(LocalDateTime.class))).thenReturn(List.of());

        var response = service.suspend(42L, "safety incident", "admin");

        assertThat(response.getStatus()).isEqualTo(PtStatus.SUSPENDED);
        assertThat(profile.getSuspensionReason()).isEqualTo("safety incident");
        assertThat(profile.isActive()).isFalse();
        verify(ptProfileRepository, never()).findById(anyLong());
        // booking + audit phải dùng PtProfile.id thật (7), không phải userId (42)
        verify(bookingRepository).findByPtProfile_IdAndStatusInAndStartAtGreaterThan(
                eq(7L), any(), any(LocalDateTime.class));
        verify(auditService).record(any(), eq("PtProfile"), eq(7L), any());
    }

    @Test
    void suspend_userWithoutPtProfile_throwsNotFound() {
        when(ptProfileRepository.findByUser_Id(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(99L, "reason", "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void suspend_alreadySuspended_throwsInvalidState() {
        when(ptProfileRepository.findByUser_Id(42L))
                .thenReturn(Optional.of(ptProfile(7L, 42L, PtStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.suspend(42L, "again", "admin"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reactivate_resolvesByUserId_andRestoresActive() {
        PtProfile profile = ptProfile(7L, 42L, PtStatus.SUSPENDED);
        profile.setSuspensionReason("old reason");
        when(ptProfileRepository.findByUser_Id(42L)).thenReturn(Optional.of(profile));

        var response = service.reactivate(42L, "admin");

        assertThat(response.getStatus()).isEqualTo(PtStatus.ACTIVE);
        assertThat(profile.getSuspensionReason()).isNull();
        assertThat(profile.isActive()).isTrue();
        verify(auditService).record(any(), eq("PtProfile"), eq(7L), any());
    }

    @Test
    void reactivate_notSuspended_throwsInvalidState() {
        when(ptProfileRepository.findByUser_Id(42L))
                .thenReturn(Optional.of(ptProfile(7L, 42L, PtStatus.ACTIVE)));

        assertThatThrownBy(() -> service.reactivate(42L, "admin"))
                .isInstanceOf(BusinessException.class);
    }
}
