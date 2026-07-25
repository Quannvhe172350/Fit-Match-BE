package com.fitmatch.service;

/**
 * Kênh gửi SMS (UC-002). Hiện chỉ có {@link com.fitmatch.service.impl.LoggingSmsService}
 * (log thay vì gửi — cùng pattern LoggingEmailService). Khi tích hợp nhà cung cấp thật
 * (eSMS/Twilio/SpeedSMS...), tạo bean @Primary theo @ConditionalOnProperty tương ứng.
 */
public interface SmsService {

    void send(String phoneNumber, String message);
}
