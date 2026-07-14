package com.fitmatch.service.support;

import com.fitmatch.common.enums.TokenType;
import com.fitmatch.entity.User;
import com.fitmatch.entity.VerificationToken;
import com.fitmatch.repository.VerificationTokenRepository;
import com.fitmatch.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phát hành token xác minh email (UC-002) — dùng chung cho đăng ký, gửi lại,
 * và đổi email trong hồ sơ (đổi email bắt buộc xác minh lại quyền sở hữu).
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationIssuer {

    private static final long EMAIL_VERIFICATION_TTL_HOURS = 24;

    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    /** Vô hiệu hoá token cũ, tạo token mới và gửi email xác minh tới email hiện tại của user. */
    public void issue(User user) {
        verificationTokenRepository.invalidateExisting(user, TokenType.EMAIL_VERIFICATION);
        VerificationToken token = VerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(EMAIL_VERIFICATION_TTL_HOURS))
                .used(false)
                .build();
        verificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
    }
}
