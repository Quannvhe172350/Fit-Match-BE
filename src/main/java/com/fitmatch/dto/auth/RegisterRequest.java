package com.fitmatch.dto.auth;

import com.fitmatch.common.enums.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /** A-5: họ tên hiển thị (tiếng Việt có dấu) — tách khỏi username (regex ASCII). */
    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = "^\\p{L}+(?: \\p{L}+)*$",
            message = "Họ tên chỉ gồm chữ cái và khoảng trắng, không chứa số hoặc ký tự đặc biệt")
    private String fullName;

    @NotBlank(message = "Password is required")
    @com.fitmatch.common.validation.StrongPassword
    private String password;

    /** UC-001: không bắt buộc, nhưng đã điền thì phải là số Việt Nam dùng được. */
    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;

    // UC-001: client chỉ được chọn AccountType (CUSTOMER | GYM_OPERATOR), không gửi Role trực tiếp
    // để tránh leo thang đặc quyền. PT do Gym tạo (UC-019); role quản trị do Admin gán (UC-077).
    private AccountType accountType;
}
