package com.fitmatch.dto.pt;

import com.fitmatch.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gym tạo tài khoản + hồ sơ PT dưới quyền quản lý của mình (UC-019).
 * Tài khoản PT nhận ROLE_PT ngay khi tạo, không qua platform verification.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGymPtRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword
    private String password;

    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;

    @NotBlank(message = "Display name is required")
    @Size(max = 120)
    private String displayName;

    @Size(max = 2000)
    private String bio;

    @Size(max = 255)
    private String specialization;

    @Size(max = 255)
    private String serviceArea;

    private Integer experienceYears;

    /**
     * Chi nhánh PT phụ trách — BẮT BUỘC ít nhất một (UC-019/022).
     *
     * PT không thuộc chi nhánh nào thì không hiện ở marketplace và mọi booking đều
     * bị từ chối, nên tạo PT "treo" chỉ tạo ra dữ liệu chết. Mọi chi nhánh phải
     * thuộc chính Gym này và đang hoạt động.
     */
    @NotEmpty(message = "At least one branch is required")
    private List<Long> branchIds;
}
