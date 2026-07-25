package com.fitmatch.service.impl;

import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;

/**
 * [SMS-STUB] Fallback khi chưa cấu hình nhà cung cấp SMS: log nội dung thay vì
 * gửi thật (dev đọc OTP từ log). Đăng ký qua {@link com.fitmatch.config.SmsServiceConfig}
 * — chỉ tạo khi không có bean SmsService nào khác (SMS_PROVIDER chưa set).
 */
@Slf4j
public class LoggingSmsService implements SmsService {

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[SMS-STUB] To: {} | {}", phoneNumber, message);
    }
}
