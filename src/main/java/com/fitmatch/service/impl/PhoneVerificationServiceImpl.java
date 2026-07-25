package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.TokenType;
import com.fitmatch.entity.User;
import com.fitmatch.entity.VerificationToken;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.VerificationTokenRepository;
import com.fitmatch.service.PhoneVerificationService;
import com.fitmatch.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UC-002: OTP 6 chữ số, TTL 10 phút, tối đa 5 lần nhập sai. Cột token là UNIQUE
 * toàn cục nên lưu dạng "{code}:{uuid}" — so khớp bằng phần code của token
 * đang hiệu lực mới nhất của user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationServiceImpl implements PhoneVerificationService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final SmsService smsService;

    @Override
    @Transactional
    public void requestOtp(String username) {
        User user = requireUser(username);
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Please add a phone number to your profile first");
        }
        if (user.isPhoneVerified()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Phone number is already verified");
        }
        tokenRepository.invalidateExisting(user, TokenType.PHONE_VERIFICATION);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        tokenRepository.save(VerificationToken.builder()
                .token(code + ":" + UUID.randomUUID())
                .user(user)
                .type(TokenType.PHONE_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build());
        smsService.send(user.getPhone(),
                "FitMatch: ma xac minh cua ban la " + code
                        + " (hieu luc " + OTP_TTL_MINUTES + " phut).");
        log.info("Phone OTP issued for user {}", username);
    }

    @Override
    @Transactional
    public void verifyOtp(String username, String code) {
        User user = requireUser(username);
        VerificationToken token = tokenRepository
                .findTopByUserAndTypeAndUsedFalseOrderByIdDesc(user, TokenType.PHONE_VERIFICATION)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID,
                        "No active OTP - please request a new code"));
        if (token.isExpired()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID,
                    "OTP has expired - please request a new code");
        }
        String expected = token.getToken().split(":", 2)[0];
        if (!expected.equals(code)) {
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= MAX_ATTEMPTS) {
                token.setUsed(true); // khóa OTP — phải xin mã mới
            }
            tokenRepository.save(token);
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID,
                    token.isUsed()
                            ? "Too many wrong attempts - please request a new code"
                            : "Incorrect OTP code");
        }
        token.setUsed(true);
        tokenRepository.save(token);
        user.setPhoneVerified(true);
        userRepository.save(user);
        log.info("Phone verified for user {}", username);
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
}
