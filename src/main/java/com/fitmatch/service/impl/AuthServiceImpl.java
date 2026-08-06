package com.fitmatch.service.impl;

import com.fitmatch.common.enums.AccountType;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.TokenType;
import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.ForgotPasswordRequest;
import com.fitmatch.dto.auth.GoogleLoginRequest;
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
import com.fitmatch.security.GoogleTokenVerifier;
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.AuthService;
import com.fitmatch.service.EmailService;
import com.fitmatch.service.support.EmailVerificationIssuer;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final GoogleTokenVerifier googleTokenVerifier;

    /** P1-1.6: số lần đăng nhập sai liên tiếp trước khi khóa tạm tài khoản. */
    @org.springframework.beans.factory.annotation.Value("${app.security.max-failed-login:5}")
    private int maxFailedLogin;

    /** P1-1.6: thời gian khóa tạm (phút) sau khi vượt ngưỡng. */
    @org.springframework.beans.factory.annotation.Value("${app.security.lockout-minutes:15}")
    private long lockoutMinutes;

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

        // P1-1.6: auto-lockout — tài khoản đang bị khóa tạm do sai mật khẩu quá nhiều lần.
        if (user != null && user.getLockoutUntil() != null
                && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
            log.warn("Login blocked (locked out until {}): {}", user.getLockoutUntil(), user.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed login attempts. Please try again later.");
        }

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            if (user != null) {
                recordFailedLogin(user);
            }
            log.debug("Login failed for username: {}", request.getUsername());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Đăng nhập đúng mật khẩu -> xóa bộ đếm/khóa (login() không @Transactional nên mỗi
        // save chạy trong tx riêng của repository -> ghi nhận thất bại vẫn được commit dù
        // sau đó ném INVALID_CREDENTIALS).
        if (user.getFailedLoginAttempts() > 0 || user.getLockoutUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            userRepository.save(user);
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

    /**
     * P1-1.6: ghi nhận một lần đăng nhập sai. Khi đạt ngưỡng -> khóa tạm tài khoản
     * {@code lockoutMinutes} phút và reset bộ đếm. Chạy trong tx riêng của repository
     * (login() không @Transactional) nên được commit trước khi login ném lỗi.
     */
    private void recordFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= maxFailedLogin) {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            log.warn("Account {} locked for {} min after {} failed login attempts",
                    user.getUsername(), lockoutMinutes, maxFailedLogin);
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        // Mọi thông tin danh tính lấy từ token đã ký, không lấy từ body request:
        // client không được phép tự khai email của người khác.
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.getIdToken());
        String googleId = payload.getSubject();
        String email = payload.getEmail().trim().toLowerCase();

        // Tra theo googleId trước rồi mới tới email: người dùng đổi địa chỉ email
        // trên Google vẫn quay về đúng tài khoản FitMatch cũ.
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);

        if (user == null) {
            user = registerGoogleUser(payload, googleId, email);
            log.info("New user registered via Google: {}", user.getUsername());
            return issueTokens(user);
        }

        if (user.getStatus() != com.fitmatch.common.enums.UserStatus.ACTIVE) {
            log.info("Google login blocked for non-active account: {} ({})", user.getUsername(), user.getStatus());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is " + user.getStatus().name().toLowerCase());
        }

        if (user.getGoogleId() == null) {
            // Tài khoản đăng ký bằng mật khẩu, nay đăng nhập Google cùng email: liên kết
            // thay vì báo EMAIL_EXISTS. An toàn vì Google đã xác minh quyền sở hữu email.
            user.setGoogleId(googleId);
            log.info("Linked Google account to existing user: {}", user.getUsername());
        } else if (!user.getGoogleId().equals(googleId)) {
            // Cùng email nhưng khác `sub` (email Workspace bị xoá rồi tạo lại) — giữ liên
            // kết cũ, không ghi đè, nhưng vẫn cho đăng nhập vì email đã được xác minh.
            log.warn("Google sub mismatch for {} - keeping the existing link", user.getUsername());
        }
        if (!user.isEmailVerified()) {
            // Đăng ký bằng mật khẩu nhưng chưa bấm link xác minh: Google đã xác minh hộ.
            user.setEmailVerified(true);
        }
        if (!StringUtils.hasText(user.getAvatarUrl())) {
            user.setAvatarUrl(pictureOf(payload));
        }
        // P1-1.6: khóa tạm là cơ chế chống dò MẬT KHẨU — luồng Google không dò được
        // mật khẩu nên không bị chặn, và danh tính đã được Google kiểm chứng nên xóa
        // luôn bộ đếm sai.
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        log.info("User logged in via Google: {}", user.getUsername());
        return issueTokens(user);
    }

    /** UC-003: tạo tài khoản CUSTOMER từ hồ sơ Google đã xác minh. */
    private User registerGoogleUser(GoogleIdToken.Payload payload, String googleId, String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        String fullName = claim(payload, "name");

        User user = User.builder()
                .username(generateUsername(localPart))
                .email(email)
                .fullName(StringUtils.hasText(fullName) ? fullName : localPart)
                // Hash của một chuỗi ngẫu nhiên không ai biết: giữ ràng buộc NOT NULL của
                // password_hash mà vẫn không mở đường đăng nhập bằng mật khẩu. Người dùng
                // muốn có mật khẩu thì đi qua "quên mật khẩu" (UC-004).
                .passwordHash(passwordEncoder.encode(UUID.randomUUID() + ":" + UUID.randomUUID()))
                .avatarUrl(pictureOf(payload))
                .googleId(googleId)
                // Chỉ tự đăng ký được CUSTOMER: chủ phòng tập vẫn đi UC-001 (còn phải khai
                // hồ sơ gym), PT do gym tạo (UC-019) — không để Google là đường vòng cấp role.
                .role(AccountType.CUSTOMER.getRole())
                .status(com.fitmatch.common.enums.UserStatus.ACTIVE)
                // Google đã xác minh email -> bỏ qua bước gửi mail xác minh.
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    /**
     * Sinh username từ phần trước @ của email, lọc theo đúng bộ ký tự UC-001 cho phép
     * và thêm hậu tố số khi trùng.
     */
    private String generateUsername(String localPart) {
        String base = localPart.replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 1; i <= 99; i++) {
            String candidate = base + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Ảnh đại diện Google; bỏ qua URL dài hơn cột avatar_url (varchar 255). */
    private String pictureOf(GoogleIdToken.Payload payload) {
        String picture = claim(payload, "picture");
        return picture != null && picture.length() <= 255 ? picture : null;
    }

    private String claim(GoogleIdToken.Payload payload, String name) {
        return payload.get(name) instanceof String value && StringUtils.hasText(value) ? value : null;
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
