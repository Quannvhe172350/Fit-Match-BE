package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.UserServiceImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailVerificationIssuer emailVerificationIssuer;
    @InjectMocks private UserServiceImpl service;

    private User user(String email, boolean verified) {
        return User.builder()
                .username("john")
                .email(email)
                .emailVerified(verified)
                .build();
    }

    @Test
    void updateProfile_emailChanged_requiresReVerification() {
        User u = user("old@x.com", true);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = service.updateProfile("john",
                UpdateProfileRequest.builder().email("new@x.com").build());

        assertThat(u.getEmail()).isEqualTo("new@x.com");
        assertThat(u.isEmailVerified()).isFalse();
        verify(emailVerificationIssuer).issue(u);
    }

    @Test
    void updateProfile_emailTaken_throwsConflict() {
        User u = user("old@x.com", true);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile("john",
                UpdateProfileRequest.builder().email("taken@x.com").build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_EXISTS);
        assertThat(u.getEmail()).isEqualTo("old@x.com");
        assertThat(u.isEmailVerified()).isTrue();
    }

    @Test
    void updateProfile_sameEmail_keepsVerifiedAndNoToken() {
        User u = user("same@x.com", true);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile("john",
                UpdateProfileRequest.builder().email("SAME@x.com").fullName("John D").build());

        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getFullName()).isEqualTo("John D");
        verify(emailVerificationIssuer, never()).issue(any());
    }
}
