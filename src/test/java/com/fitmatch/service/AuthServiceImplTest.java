package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.VerificationTokenRepository;
import com.fitmatch.security.GoogleTokenVerifier;
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.impl.AuthServiceImpl;
import com.fitmatch.service.support.EmailVerificationIssuer;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationIssuer emailVerificationIssuer;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @InjectMocks private AuthServiceImpl service;

    private User user(UserStatus status, int tokenVersion) {
        User u = User.builder()
                .username("john")
                .email("john@x.com")
                .passwordHash("hash")
                .role(Role.ROLE_CUSTOMER)
                .status(status)
                .build();
        u.setTokenVersion(tokenVersion);
        return u;
    }

    private void stubTokenIssue() {
        lenient().when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyInt()))
                .thenReturn("access");
        lenient().when(jwtTokenProvider.generateRefreshToken(anyString(), anyInt()))
                .thenReturn("refresh");
    }

    @Test
    void register_doesNotIssueTokens_andSendsVerification() {
        // BE-2 (audit 2026-07-17): token cấp lúc đăng ký là đường vòng qua chính sách
        // verify email (login chặn EMAIL_NOT_VERIFIED nhưng token register thì không).
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@x.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(com.fitmatch.dto.auth.RegisterRequest.builder()
                .username("john").email("john@x.com").password("secret")
                .fullName("Nguyễn Văn A").build());

        org.mockito.Mockito.verify(emailVerificationIssuer).issue(any(User.class));
        org.mockito.Mockito.verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void login_byEmail_succeeds() {
        // A-4: FE label là "Email" — BE chấp nhận cả username lẫn email.
        User u = user(UserStatus.ACTIVE, 0);
        u.setEmailVerified(true);
        stubTokenIssue();
        when(userRepository.findByUsername("john@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@x.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        var res = service.login(new LoginRequest("john@x.com", "secret"));

        assertThat(res.getAccessToken()).isEqualTo("access");
    }

    @Test
    void login_wrongPassword_returnsInvalidCredentials() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user(UserStatus.ACTIVE, 0)));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("john", "bad")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_bannedAccountWithCorrectPassword_returnsAccountLocked() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user(UserStatus.BANNED, 0)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("john", "secret")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void login_unverifiedEmail_returnsEmailNotVerified() {
        // P1-11: active + đúng mật khẩu nhưng chưa verify email -> chặn login.
        User u = user(UserStatus.ACTIVE, 0); // helper build với emailVerified=false
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("john", "secret")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void login_verifiedActive_succeeds() {
        User u = user(UserStatus.ACTIVE, 0);
        u.setEmailVerified(true);
        stubTokenIssue();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        var res = service.login(new LoginRequest("john", "secret"));

        assertThat(res.getAccessToken()).isEqualTo("access");
    }

    @Test
    void login_reachingThreshold_locksAccount() {
        // P1-1.6: lần đăng nhập sai thứ N (ngưỡng) -> khóa tạm + reset bộ đếm.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxFailedLogin", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "lockoutMinutes", 15L);
        User u = user(UserStatus.ACTIVE, 0);
        u.setFailedLoginAttempts(2); // lần sai kế tiếp là lần thứ 3
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("john", "bad")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(u.getLockoutUntil()).isNotNull();
        assertThat(u.getFailedLoginAttempts()).isZero();
        org.mockito.Mockito.verify(userRepository).save(u);
    }

    @Test
    void login_whenLockedOut_returnsAccountLocked() {
        // P1-1.6: đang trong thời gian khóa -> chặn ngay, không cần kiểm mật khẩu.
        User u = user(UserStatus.ACTIVE, 0);
        u.setLockoutUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("john", "secret")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void login_success_resetsFailedAttempts() {
        User u = user(UserStatus.ACTIVE, 0);
        u.setEmailVerified(true);
        u.setFailedLoginAttempts(2);
        stubTokenIssue();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        service.login(new LoginRequest("john", "secret"));

        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.getLockoutUntil()).isNull();
        org.mockito.Mockito.verify(userRepository).save(u);
    }

    // ─── UC-003: đăng nhập Google ─────────────────────────────────────────────

    private GoogleIdToken.Payload googlePayload() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("john@x.com");
        payload.setEmailVerified(true);
        payload.set("name", "Nguyễn Văn A");
        payload.set("picture", "https://lh3.googleusercontent.com/a/avatar");
        return payload;
    }

    @Test
    void googleLogin_newEmail_createsVerifiedCustomer() {
        stubTokenIssue();
        when(googleTokenVerifier.verify("id-token")).thenReturn(googlePayload());
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@x.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.googleLogin(new com.fitmatch.dto.auth.GoogleLoginRequest("id-token"));

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("john");
        assertThat(saved.getValue().getGoogleId()).isEqualTo("google-sub-1");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ROLE_CUSTOMER);
        // Google đã xác minh email -> không phải bấm link verify mới đăng nhập được.
        assertThat(saved.getValue().isEmailVerified()).isTrue();
        assertThat(res.getAccessToken()).isEqualTo("access");
    }

    @Test
    void googleLogin_usernameTaken_appendsSuffix() {
        stubTokenIssue();
        when(googleTokenVerifier.verify("id-token")).thenReturn(googlePayload());
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@x.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("john")).thenReturn(true);
        when(userRepository.existsByUsername("john1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.googleLogin(new com.fitmatch.dto.auth.GoogleLoginRequest("id-token"));

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("john1");
    }

    @Test
    void googleLogin_existingEmail_linksAccountAndVerifiesEmail() {
        // Đăng ký bằng mật khẩu, chưa bấm link verify, nay đăng nhập bằng Google
        // cùng email -> liên kết vào tài khoản cũ thay vì báo trùng email.
        User u = user(UserStatus.ACTIVE, 0);
        stubTokenIssue();
        when(googleTokenVerifier.verify("id-token")).thenReturn(googlePayload());
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@x.com")).thenReturn(Optional.of(u));

        var res = service.googleLogin(new com.fitmatch.dto.auth.GoogleLoginRequest("id-token"));

        assertThat(u.getGoogleId()).isEqualTo("google-sub-1");
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(res.getAccessToken()).isEqualTo("access");
        org.mockito.Mockito.verify(userRepository).save(u);
    }

    @Test
    void googleLogin_lockedOutAccount_stillSucceeds() {
        // P1-1.6: khóa tạm chống dò MẬT KHẨU — Google không dò mật khẩu nên không bị
        // chặn, và bộ đếm được xóa vì danh tính đã do Google kiểm chứng.
        User u = user(UserStatus.ACTIVE, 0);
        u.setEmailVerified(true);
        u.setGoogleId("google-sub-1");
        u.setFailedLoginAttempts(4);
        u.setLockoutUntil(java.time.LocalDateTime.now().plusMinutes(10));
        stubTokenIssue();
        when(googleTokenVerifier.verify("id-token")).thenReturn(googlePayload());
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.of(u));

        service.googleLogin(new com.fitmatch.dto.auth.GoogleLoginRequest("id-token"));

        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.getLockoutUntil()).isNull();
    }

    @Test
    void googleLogin_bannedAccount_returnsAccountLocked() {
        User u = user(UserStatus.BANNED, 0);
        u.setGoogleId("google-sub-1");
        when(googleTokenVerifier.verify("id-token")).thenReturn(googlePayload());
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.googleLogin(new com.fitmatch.dto.auth.GoogleLoginRequest("id-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void logout_revokesAllTokensByBumpingVersion() {
        User u = user(UserStatus.ACTIVE, 3);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.logout("john");

        assertThat(u.getTokenVersion()).isEqualTo(4);
    }

    @Test
    void changePassword_revokesAllTokens() {
        User u = user(UserStatus.ACTIVE, 1);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("newhash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword("john", new ChangePasswordRequest("old", "newpass"));

        assertThat(u.getTokenVersion()).isEqualTo(2);
        assertThat(u.getPasswordHash()).isEqualTo("newhash");
    }

    @Test
    void refreshToken_staleVersion_rejected() {
        User u = user(UserStatus.ACTIVE, 5);
        when(jwtTokenProvider.validateRefreshToken("stale")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromRefreshToken("stale")).thenReturn("john");
        when(jwtTokenProvider.getVersionFromRefreshToken("stale")).thenReturn(4);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenRequest("stale")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void refreshToken_currentVersion_issuesNewPair() {
        User u = user(UserStatus.ACTIVE, 5);
        stubTokenIssue();
        when(jwtTokenProvider.validateRefreshToken("ok")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromRefreshToken("ok")).thenReturn("john");
        when(jwtTokenProvider.getVersionFromRefreshToken("ok")).thenReturn(5);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));

        var res = service.refreshToken(new RefreshTokenRequest("ok"));

        assertThat(res.getAccessToken()).isEqualTo("access");
        assertThat(res.getRefreshToken()).isEqualTo("refresh");
    }
}
