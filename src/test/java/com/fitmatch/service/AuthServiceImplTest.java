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
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.impl.AuthServiceImpl;
import com.fitmatch.service.support.EmailVerificationIssuer;
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
