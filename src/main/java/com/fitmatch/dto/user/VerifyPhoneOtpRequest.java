package com.fitmatch.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** UC-002: xác thực OTP 6 chữ số gửi qua SMS. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPhoneOtpRequest {

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String code;
}
