package com.fitmatch.service;

import com.fitmatch.dto.user.NotificationPreferenceResponse;
import com.fitmatch.dto.user.UpdateNotificationPreferenceRequest;

public interface NotificationPreferenceService {

    /** UC-07: xem tùy chọn thông báo (tự tạo mặc định nếu chưa có). */
    NotificationPreferenceResponse getPreferences(String username);

    /** UC-07: cập nhật một phần tùy chọn thông báo. */
    NotificationPreferenceResponse updatePreferences(String username, UpdateNotificationPreferenceRequest request);
}
