package com.fitmatch.service.impl;

import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * [SMS-STUB] Fallback khi chưa cấu hình nhà cung cấp SMS: log nội dung thay vì
 * gửi thật (dev đọc OTP từ log). Bean provider thật sẽ thay thế nhờ
 * {@code @ConditionalOnMissingBean}.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(name = "smsProvider")
public class LoggingSmsService implements SmsService {

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[SMS-STUB] To: {} | {}", phoneNumber, message);
    }
}
