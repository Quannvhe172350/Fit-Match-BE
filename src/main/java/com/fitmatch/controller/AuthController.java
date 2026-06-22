package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.auth.AuthResponse;
import com.fitmatch.dto.auth.ChangePasswordRequest;
import com.fitmatch.dto.auth.LoginRequest;
import com.fitmatch.dto.auth.ForgotPasswordRequest;
import com.fitmatch.dto.auth.RefreshTokenRequest;
import com.fitmatch.dto.auth.RegisterRequest;
import com.fitmatch.dto.auth.ResendVerificationRequest;
import com.fitmatch.dto.auth.ResetPasswordRequest;
import com.fitmatch.dto.auth.VerifyEmailRequest;
import com.fitmatch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "A. Authentication", description = "Đăng ký, đăng nhập, xác minh email, đổi/đặt lại mật khẩu (UC-01 → UC-06)")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "UC-01 — Đăng ký tài khoản",
            description = """
                    Actor: **Guest**. Tạo tài khoản mới với role mặc định ROLE_CUSTOMER (không nhận role từ client).
                    Gửi email xác minh (stub log) và trả về access/refresh token.
                    Lỗi: 409 nếu username/email đã tồn tại; 400 nếu validation thất bại.
                    """)
    @SecurityRequirements // public
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please verify your email.", response));
    }

    @Operation(
            summary = "UC-01 — Xác minh email",
            description = "Actor: **Guest**. Xác minh email bằng token một lần (TTL 24h). Lỗi: 400 token không hợp lệ/hết hạn; 409 email đã xác minh.")
    @SecurityRequirements // public
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    @Operation(
            summary = "UC-01 — Gửi lại email xác minh",
            description = "Actor: **Guest**. Phát hành lại token xác minh cho email chưa verify. Lỗi: 404 không tìm thấy email; 409 đã xác minh.")
    @SecurityRequirements // public
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent", null));
    }

    @Operation(
            summary = "UC-04 — Quên mật khẩu",
            description = "Actor: **All**. Gửi token đặt lại mật khẩu qua email (TTL 60 phút). Luôn trả 200 dù email không tồn tại (chống account enumeration).")
    @SecurityRequirements // public
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If the email exists, a reset link has been sent", null));
    }

    @Operation(
            summary = "UC-04 — Đặt lại mật khẩu",
            description = "Actor: **All**. Đặt lại mật khẩu bằng token reset. Lỗi: 400 token không hợp lệ/hết hạn.")
    @SecurityRequirements // public
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully", null));
    }

    @Operation(
            summary = "UC-02 — Đăng nhập",
            description = "Actor: **All**. Trả về access token + refresh token. Lỗi: 401 sai thông tin đăng nhập; 403 tài khoản bị khoá.")
    @SecurityRequirements // public
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @Operation(
            summary = "UC-02 — Làm mới token",
            description = "Actor: **All**. Cấp access token mới từ refresh token hợp lệ. Lỗi: 401 refresh token không hợp lệ/hết hạn.")
    @SecurityRequirements // public
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @Operation(
            summary = "UC-03 — Đăng xuất",
            description = "Actor: **Authenticated**. JWT stateless: client tự huỷ token. Yêu cầu Bearer token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    @Operation(
            summary = "UC-06 — Đổi mật khẩu",
            description = "Actor: **Authenticated**. Đổi mật khẩu khi biết mật khẩu cũ. Lỗi: 401 mật khẩu cũ sai.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
