package com.fitmatch.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tất cả trường là Boolean (nullable) để hỗ trợ cập nhật một phần (partial update):
 * trường null nghĩa là giữ nguyên giá trị hiện tại.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationPreferenceRequest {

    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean marketingEnabled;
    private Boolean bookingReminders;
}
