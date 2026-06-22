package com.fitmatch.service;

import com.fitmatch.common.enums.TokenType;
import com.fitmatch.dto.auth.ResendVerificationRequest;
import com.fitmatch.dto.auth.ResetPasswordRequest;
import com.fitmatch.dto.auth.VerifyEmailRequest;
import com.fitmatch.entity.User;
import com.fitmatch.entity.VerificationToken;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.VerificationTokenRepository;
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserDetailsService userDetailsService;
    @Mock private EmailService emailService;

    @InjectMocks private AuthServiceImpl authService;

    private VerificationToken validToken(User user) {
        return VerificationToken.builder()
                .token("tok-123")
                .user(user)
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
    }

    @Test
    void verifyEmail_validToken_marksUserVerified() {
        User user = User.builder().username("john").email("j@x.com").emailVerified(false).build();
        VerificationToken token = validToken(user);
        when(verificationTokenRepository.findByTokenAndType("tok-123", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        authService.verifyEmail(VerifyEmailRequest.builder().token("tok-123").build());

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_expiredToken_throws() {
        User user = User.builder().username("john").emailVerified(false).build();
        VerificationToken token = validToken(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(verificationTokenRepository.findByTokenAndType("tok-123", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(VerifyEmailRequest.builder().token("tok-123").build()))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_unknownToken_throws() {
        when(verificationTokenRepository.findByTokenAndType(eq("nope"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(VerifyEmailRequest.builder().token("nope").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndConsumesToken() {
        User user = User.builder().username("john").passwordHash("old").build();
        VerificationToken token = VerificationToken.builder()
                .token("rst-1").user(user).type(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).used(false).build();
        when(verificationTokenRepository.findByTokenAndType("rst-1", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpass")).thenReturn("hashed");

        authService.resetPassword(ResetPasswordRequest.builder().token("rst-1").newPassword("newpass").build());

        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_usedToken_throws() {
        User user = User.builder().username("john").build();
        VerificationToken token = VerificationToken.builder()
                .token("rst-2").user(user).type(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).used(true).build();
        when(verificationTokenRepository.findByTokenAndType("rst-2", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(
                ResetPasswordRequest.builder().token("rst-2").newPassword("newpass").build()))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerification_alreadyVerified_throws() {
        User user = User.builder().username("john").email("j@x.com").emailVerified(true).build();
        when(userRepository.findByEmail("j@x.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resendVerification(
                ResendVerificationRequest.builder().email("j@x.com").build()))
                .isInstanceOf(BusinessException.class);
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }
}
