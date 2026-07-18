package com.fitmatch.service.impl;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.notification.NotificationResponse;
import com.fitmatch.entity.Notification;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.NotificationPreferenceRepository;
import com.fitmatch.repository.NotificationRepository;
import com.fitmatch.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final com.fitmatch.service.EmailService emailService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User user, NotificationCategory category, String title, String body, String link) {
        doNotify(user, category, title, body, link);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(String username, NotificationCategory category, String title, String body, String link) {
        if (username == null) return;
        doNotify(userRepository.findByUsername(username).orElse(null), category, title, body, link);
    }

    /** REQUIRES_NEW + nuốt lỗi: thông báo là phụ trợ, không được làm rollback
     *  hay chặn giao dịch nghiệp vụ chính. */
    private void doNotify(User user, NotificationCategory category, String title, String body, String link) {
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
            // Bug 9 (UC-075): thông báo giao dịch (booking/thanh toán) gửi kèm
            // email khi người dùng bật "Email thông báo" (mặc định bật).
            if ((category == NotificationCategory.BOOKING || category == NotificationCategory.PAYMENT)
                    && user.getEmail() != null && !user.getEmail().isBlank()) {
                boolean emailAllowed = preferenceRepository.findByUser_Username(user.getUsername())
                        .map(p -> p.isEmailEnabled()).orElse(true);
                if (emailAllowed) {
                    emailService.sendNotificationEmail(user.getEmail(), title, body, link);
                }
            }
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
