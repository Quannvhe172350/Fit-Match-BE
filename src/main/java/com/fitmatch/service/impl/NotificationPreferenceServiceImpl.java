package com.fitmatch.service.impl;

import com.fitmatch.dto.user.NotificationPreferenceResponse;
import com.fitmatch.dto.user.UpdateNotificationPreferenceRequest;
import com.fitmatch.entity.NotificationPreference;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.NotificationPreferenceRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public NotificationPreferenceResponse getPreferences(String username) {
        return NotificationPreferenceResponse.of(getOrCreate(username));
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreferences(String username, UpdateNotificationPreferenceRequest request) {
        NotificationPreference pref = getOrCreate(username);

        if (request.getEmailEnabled() != null) {
            pref.setEmailEnabled(request.getEmailEnabled());
        }
        if (request.getPushEnabled() != null) {
            pref.setPushEnabled(request.getPushEnabled());
        }
        if (request.getMarketingEnabled() != null) {
            pref.setMarketingEnabled(request.getMarketingEnabled());
        }
        if (request.getBookingReminders() != null) {
            pref.setBookingReminders(request.getBookingReminders());
        }

        pref = preferenceRepository.save(pref);
        log.info("Notification preferences updated for user: {}", username);
        return NotificationPreferenceResponse.of(pref);
    }

    private NotificationPreference getOrCreate(String username) {
        return preferenceRepository.findByUser_Username(username)
                .orElseGet(() -> {
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new ResourceNotFoundException("User", username));
                    return preferenceRepository.save(NotificationPreference.builder().user(user).build());
                });
    }
}
