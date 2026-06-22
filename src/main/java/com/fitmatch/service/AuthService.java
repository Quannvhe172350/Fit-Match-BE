package com.fitmatch.service;

import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.dto.auth.ForgotPasswordRequest;
import com.fitmatch.dto.auth.RegisterRequest;
import com.fitmatch.dto.auth.ResendVerificationRequest;
import com.fitmatch.dto.auth.ResetPasswordRequest;
import com.fitmatch.dto.auth.VerifyEmailRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String username);

    void changePassword(String username, ChangePasswordRequest request);

    /** UC-01: xác minh email bằng token một lần. */
    void verifyEmail(VerifyEmailRequest request);

    /** UC-01: phát hành lại token xác minh cho email chưa verify. */
    void resendVerification(ResendVerificationRequest request);

    /** UC-04: yêu cầu đặt lại mật khẩu — gửi token reset qua email (không tiết lộ email tồn tại hay không). */
    void forgotPassword(ForgotPasswordRequest request);

    /** UC-04: đặt lại mật khẩu bằng token reset hợp lệ. */
    void resetPassword(ResetPasswordRequest request);
}
