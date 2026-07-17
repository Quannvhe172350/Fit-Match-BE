package com.fitmatch.service.support;

import com.fitmatch.common.enums.NotificationCategory;

/**
 * Sự kiện yêu cầu gửi thông báo (UC-075, P2). Được publish TRONG giao dịch
 * nghiệp vụ và chỉ thực sự gửi khi giao dịch COMMIT thành công (xem
 * {@link NotificationEventListener} với {@code @TransactionalEventListener}).
 *
 * <p>Trước đây {@code NotificationService.notify} chạy {@code REQUIRES_NEW} nên
 * commit độc lập -> nghiệp vụ rollback vẫn để lại "thông báo ma" (vd "Lịch đã
 * xác nhận" trong khi accept đã fail). Mang theo username (không phải entity)
 * để tránh truy cập lazy sau khi session đóng.
 */
public record NotificationEvent(
        String username,
        NotificationCategory category,
        String title,
        String body,
        String link) {
}
