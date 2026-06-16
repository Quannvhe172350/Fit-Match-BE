package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.dto.auth.RegisterRequest;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.security.JwtTokenProvider;
import com.fitmatch.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS,
                    "Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS,
                    "Email '" + request.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .status(com.fitmatch.common.enums.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} with role: {}", user.getUsername(), user.getRole());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return AuthResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        } catch (Exception e) {
            log.debug("Login failed for username: {}", request.getUsername());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
            log.debug("Invalid password for username: {}", request.getUsername());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "User has no role"));

        String accessToken = jwtTokenProvider.generateAccessToken(userDetails.getUsername(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUsername());

        log.info("User logged in: {}", request.getUsername());
        return AuthResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        jwtTokenProvider.validateRefreshToken(request.getRefreshToken());

        String username = jwtTokenProvider.getUsernameFromRefreshToken(request.getRefreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        log.debug("Token refreshed for user: {}", username);
        return AuthResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Override
    public void logout(String username) {
        log.info("User logged out: {}", username);
        // Stateless JWT — client must discard tokens.
        // Token blacklisting via Redis can be added later.
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
        userRepository.save(user);
        log.info("Password changed for user: {}", username);
    }
}
