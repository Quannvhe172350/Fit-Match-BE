package com.fitmatch.service.impl;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.notification.NotificationResponse;
import com.fitmatch.entity.Notification;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.NotificationPreferenceRepository;
import com.fitmatch.repository.NotificationRepository;
import com.fitmatch.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User user, NotificationCategory category, String title, String body, String link) {
        // REQUIRES_NEW + nuốt lỗi: thông báo là phụ trợ, không được làm rollback
        // hay chặn giao dịch nghiệp vụ chính.
        try {
            if (user == null) return;
            // MARKETING tôn trọng opt-in; các nhóm giao dịch luôn vào hộp thư in-app.
            if (category == NotificationCategory.MARKETING) {
                boolean allowed = preferenceRepository.findByUser_Username(user.getUsername())
                        .map(p -> p.isMarketingEnabled()).orElse(false);
                if (!allowed) return;
            }
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .category(category)
                    .title(title)
                    .body(body)
                    .link(link)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to create notification for {}: {}",
                    user != null ? user.getUsername() : "?", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String username, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findByUser_UsernameOrderByIdDesc(username, pageable),
                NotificationResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return notificationRepository.countByUser_UsernameAndReadAtIsNull(username);
    }

    @Override
    @Transactional
    public void markRead(String username, Long id) {
        Notification n = notificationRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public int markAllRead(String username) {
        return notificationRepository.markAllRead(username, LocalDateTime.now());
    }
}
