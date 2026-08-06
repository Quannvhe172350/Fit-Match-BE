package com.fitmatch.service;

import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.dto.auth.ForgotPasswordRequest;
import com.fitmatch.dto.auth.GoogleLoginRequest;
import com.fitmatch.dto.auth.RegisterRequest;
import com.fitmatch.dto.auth.ResendVerificationRequest;
import com.fitmatch.dto.auth.ResetPasswordRequest;
import com.fitmatch.dto.auth.VerifyEmailRequest;

public interface AuthService {

    /**
     * UC-001. KHÔNG phát hành token (BE-2, audit 2026-07-17): login đã chặn
     * EMAIL_NOT_VERIFIED nên token cấp lúc đăng ký là đường vòng qua chính sách verify.
     */
    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /**
     * UC-003: đăng nhập/đăng ký bằng Google ID token.
     *
     * <p>Chưa có tài khoản -> tạo mới role CUSTOMER, email coi như đã xác minh
     * (Google đã kiểm chứng). Đã có tài khoản cùng email -> liên kết Google vào
     * tài khoản đó thay vì báo trùng email.
     */
    AuthResponse googleLogin(GoogleLoginRequest request);

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
