package com.fitmatch.service.impl;

import com.fitmatch.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Impl stub của {@link EmailService}: ghi nội dung email ra log thay vì gửi thật (D-02).
 * Cho phép dev/test luồng xác minh email & reset mật khẩu mà không cần SMTP.
 */
@Slf4j
@Service
public class LoggingEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String to, String token) {
        log.info("[EMAIL-STUB] Verification email -> {} | verify token: {}", to, token);
    }

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        log.info("[EMAIL-STUB] Password reset email -> {} | reset token: {}", to, token);
    }
}
