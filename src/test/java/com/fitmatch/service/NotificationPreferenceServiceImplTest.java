package com.fitmatch.service;

import com.fitmatch.dto.user.NotificationPreferenceResponse;
import com.fitmatch.dto.user.UpdateNotificationPreferenceRequest;
import com.fitmatch.entity.NotificationPreference;
import com.fitmatch.entity.User;
import com.fitmatch.repository.NotificationPreferenceRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.NotificationPreferenceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private NotificationPreferenceServiceImpl service;

    @Test
    void getPreferences_createsDefaultsWhenMissing() {
        User user = User.builder().id(1L).username("john").build();
        when(preferenceRepository.findByUser_Username("john")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse res = service.getPreferences("john");

        assertThat(res.isEmailEnabled()).isTrue();
        assertThat(res.isMarketingEnabled()).isFalse();
    }

    @Test
    void updatePreferences_partialUpdate_keepsUnsetFields() {
        NotificationPreference pref = NotificationPreference.builder()
                .user(User.builder().username("john").build())
                .emailEnabled(true).pushEnabled(true).marketingEnabled(false).bookingReminders(true)
                .build();
        when(preferenceRepository.findByUser_Username("john")).thenReturn(Optional.of(pref));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse res = service.updatePreferences("john",
                UpdateNotificationPreferenceRequest.builder().marketingEnabled(true).build());

        assertThat(res.isMarketingEnabled()).isTrue();
        assertThat(res.isEmailEnabled()).isTrue(); // giữ nguyên
    }
}
