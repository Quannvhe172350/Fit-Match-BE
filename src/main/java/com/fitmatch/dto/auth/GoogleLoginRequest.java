package com.fitmatch.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UC-003: đăng nhập bằng Google. FE gửi ID token (JWT) lấy từ Google Identity
 * Services — KHÔNG gửi email/tên, vì mọi thông tin danh tính phải suy ra từ
 * token đã ký để client không thể tự khai email của người khác.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
