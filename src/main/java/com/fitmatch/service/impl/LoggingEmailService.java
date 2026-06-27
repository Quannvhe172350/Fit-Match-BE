package com.fitmatch.service.impl;

import com.fitmatch.service.EmailService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String to, String token) {
        log.info("[EMAIL-STUB] Verification email -> {} | token: {}", to, token);
    }

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        log.info("[EMAIL-STUB] Password reset email -> {} | token: {}", to, token);
    }
}
