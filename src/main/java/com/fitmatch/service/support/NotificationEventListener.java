package com.fitmatch.service.support;

import com.fitmatch.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi thông báo SAU KHI giao dịch nghiệp vụ commit thành công (UC-075, P2).
 *
 * <p>{@code AFTER_COMMIT}: nếu giao dịch rollback thì sự kiện bị bỏ qua -> không
 * còn "thông báo ma". {@code fallbackExecution = true}: nếu publish ngoài giao
 * dịch (hiếm) vẫn gửi ngay. {@code NotificationService.notify} là REQUIRES_NEW
 * nên mở giao dịch riêng để lưu (và nuốt lỗi), không ảnh hưởng nghiệp vụ.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationEvent e) {
        notificationService.notify(e.username(), e.category(), e.title(), e.body(), e.link());
    }
}
