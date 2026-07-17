package com.fitmatch.service;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.entity.Notification;
import com.fitmatch.entity.NotificationPreference;
import com.fitmatch.entity.User;
import com.fitmatch.repository.NotificationPreferenceRepository;
import com.fitmatch.repository.NotificationRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private NotificationServiceImpl service;

    private final User user = User.builder().username("john").build();

    @Test
    void notifyByUsername_looksUpUserAndStores() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        service.notify("john", NotificationCategory.BOOKING, "t", "b", "/x");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyByUsername_unknownUser_noop() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        service.notify("ghost", NotificationCategory.BOOKING, "t", "b", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notify_transactionalCategory_alwaysStored() {
        service.notify(user, NotificationCategory.BOOKING, "t", "b", "/x");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notify_marketingWithOptIn_stored() {
        when(preferenceRepository.findByUser_Username("john"))
                .thenReturn(Optional.of(NotificationPreference.builder().marketingEnabled(true).build()));

        service.notify(user, NotificationCategory.MARKETING, "promo", "b", "/x");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notify_marketingWithoutOptIn_skipped() {
        when(preferenceRepository.findByUser_Username("john"))
                .thenReturn(Optional.of(NotificationPreference.builder().marketingEnabled(false).build()));

        service.notify(user, NotificationCategory.MARKETING, "promo", "b", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notify_nullUser_noop() {
        service.notify((User) null, NotificationCategory.BOOKING, "t", "b", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notify_repositoryThrows_swallowed() {
        when(notificationRepository.save(any())).thenThrow(new RuntimeException("db down"));

        // Không được ném ra ngoài — thông báo là phụ trợ.
        service.notify(user, NotificationCategory.PAYMENT, "t", "b", "/x");
    }
}
