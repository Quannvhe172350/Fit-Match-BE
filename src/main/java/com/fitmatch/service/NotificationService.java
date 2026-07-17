package com.fitmatch.service;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.notification.NotificationResponse;
import com.fitmatch.entity.User;
import org.springframework.data.domain.Pageable;

/**
 * Thông báo in-app theo sự kiện (UC-075). {@code notify} không bao giờ ném lỗi
 * vào luồng nghiệp vụ (gửi thông báo thất bại không được chặn booking/payment).
 */
public interface NotificationService {

    /** Gửi thông báo tới một user; tôn trọng NotificationPreference; nuốt mọi lỗi. */
    void notify(User user, NotificationCategory category, String title, String body, String link);

    /**
     * Gửi thông báo theo username (tra cứu user trong giao dịch riêng). Dùng bởi
     * {@link com.fitmatch.service.support.NotificationEventListener} sau khi
     * giao dịch nghiệp vụ commit — tránh entity lazy detached.
     */
    void notify(String username, NotificationCategory category, String title, String body, String link);

    PageResponse<NotificationResponse> list(String username, Pageable pageable);

    long unreadCount(String username);

    void markRead(String username, Long id);

    int markAllRead(String username);
}
