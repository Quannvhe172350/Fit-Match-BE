package com.fitmatch.service.impl;

import com.fitmatch.common.enums.AccountType;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.TokenType;
import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.ForgotPasswordRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.dto.auth.RegisterRequest;
import com.fitmatch.dto.auth.ResendVerificationRequest;
import com.fitmatch.dto.auth.ResetPasswordRequest;
import com.fitmatch.dto.auth.VerifyEmailRequest;
import com.fitmatch.entity.User;
import com.fitmatch.entity.VerificationToken;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.VerificationTokenRepository;
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.AuthService;
import com.fitmatch.service.EmailService;
import com.fitmatch.service.support.EmailVerificationIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long PASSWORD_RESET_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final EmailVerificationIssuer emailVerificationIssuer;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS,
                    "Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS,
                    "Email '" + request.getEmail() + "' is already registered");
        }

        // UC-001: role suy ra từ AccountType (CUSTOMER | GYM_OPERATOR), mặc định CUSTOMER.
        // Gym Operator có role ngay nhưng chỉ dùng được tính năng provider sau khi Gym được duyệt (UC-013).
        AccountType accountType = request.getAccountType() != null
                ? request.getAccountType()
                : AccountType.CUSTOMER;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(accountType.getRole())
                .status(com.fitmatch.common.enums.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} with role: {}", user.getUsername(), user.getRole());

        // UC-01: phát hành token xác minh email và gửi qua EmailService (stub).
        emailVerificationIssuer.issue(user);

        // BE-2 (audit 2026-07-17): KHÔNG issueTokens ở đây — user phải verify email
        // rồi login; token cấp lúc đăng ký từng cho phép gọi API bảo vệ khi chưa verify.
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // UC-003: kiểm tra mật khẩu TRƯỚC khi tiết lộ trạng thái khóa
        // (chống account enumeration nhưng vẫn trả 403 đúng cho tài khoản bị khóa).
        // A-4 (audit 2026-07-17): chấp nhận cả username lẫn email — FE label là "Email".
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.debug("Login failed for username: {}", request.getUsername());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != com.fitmatch.common.enums.UserStatus.ACTIVE) {
            log.info("Login blocked for non-active account: {} ({})", user.getUsername(), user.getStatus());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is " + user.getStatus().name().toLowerCase());
        }
        // UC-001/002 (P1-11): tài khoản phải xác minh email mới đủ điều kiện cho các
        // hành động được bảo vệ. Kiểm tra sau mật khẩu/khoá để không lộ trạng thái.
        if (!user.isEmailVerified()) {
            log.info("Login blocked for unverified email: {}", user.getUsername());
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        log.info("User logged in: {}", request.getUsername());
        return issueTokens(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        jwtTokenProvider.validateRefreshToken(request.getRefreshToken());

        String username = jwtTokenProvider.getUsernameFromRefreshToken(request.getRefreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        // UC-003: refresh token phát hành trước lần logout/đổi mật khẩu gần nhất bị từ chối.
        if (jwtTokenProvider.getVersionFromRefreshToken(request.getRefreshToken()) != user.getTokenVersion()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Refresh token has been revoked");
        }
        if (user.getStatus() != com.fitmatch.common.enums.UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is " + user.getStatus().name().toLowerCase());
        }

        log.debug("Token refreshed for user: {}", username);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(String username) {
        // UC-003: stateless JWT — tăng tokenVersion để mọi access/refresh token cũ hết hiệu lực.
        userRepository.findByUsername(username).ifPresent(this::revokeAllTokens);
        log.info("User logged out (all tokens revoked): {}", username);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // UC-004: đổi mật khẩu vô hiệu hoá mọi phiên/token đang tồn tại.
        revokeAllTokens(user);
        log.info("Password changed for user: {} (all tokens revoked)", username);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        VerificationToken token = verificationTokenRepository
                .findByTokenAndType(request.getToken(), TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        User user = token.getUser();
        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        user.setEmailVerified(true);
        token.setUsed(true);
        userRepository.save(user);
        verificationTokenRepository.save(token);
        log.info("Email verified for user: {}", user.getUsername());
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        // Không tiết lộ email có tồn tại hay không: luôn trả về thành công.
        userRepository.findByEmail(request.getEmail()).ifPresentOrElse(user -> {
            if (!user.isEmailVerified()) {
                emailVerificationIssuer.issue(user);
                log.info("Verification email re-sent to: {}", request.getEmail());
            }
        }, () -> log.debug("Resend verification requested for unknown email: {}", request.getEmail()));
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Không tiết lộ email có tồn tại hay không (chống account enumeration): luôn trả về thành công.
        userRepository.findByEmail(request.getEmail()).ifPresentOrElse(user -> {
            verificationTokenRepository.invalidateExisting(user, TokenType.PASSWORD_RESET);
            VerificationToken token = VerificationToken.builder()
                    .token(UUID.randomUUID().toString())
                    .user(user)
                    .type(TokenType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TTL_MINUTES))
                    .used(false)
                    .build();
            verificationTokenRepository.save(token);
            emailService.sendPasswordResetEmail(user.getEmail(), token.getToken());
            log.info("Password reset token issued for: {}", request.getEmail());
        }, () -> log.debug("Password reset requested for unknown email: {}", request.getEmail()));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationToken token = verificationTokenRepository
                .findByTokenAndType(request.getToken(), TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        token.setUsed(true);
        // UC-004: reset mật khẩu vô hiệu hoá mọi phiên/token đang tồn tại.
        revokeAllTokens(user);
        verificationTokenRepository.save(token);
        log.info("Password reset completed for user: {} (all tokens revoked)", user.getUsername());
    }

    /** Phát hành cặp access+refresh token gắn tokenVersion hiện tại của user. */
    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUsername(), user.getRole().name(), user.getTokenVersion());
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getUsername(), user.getTokenVersion());
        return AuthResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    /** Tăng tokenVersion — mọi JWT đã phát hành trước đó (mang ver cũ) bị từ chối. */
    private void revokeAllTokens(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }
}
