package com.fitmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạo/cập nhật tham số cấu hình hệ thống (UC-078).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigRequest {

    @NotBlank(message = "Config key is required")
    @Size(max = 100)
    @Pattern(regexp = "^[A-Z0-9_.]+$", message = "Config key must be UPPER_SNAKE_CASE (letters, digits, _ , .)")
    private String configKey;

    @NotBlank(message = "Config value is required")
    @Size(max = 1000)
    private String configValue;

    @Size(max = 500)
    private String description;
}
