package com.fitmatch.dto.gym;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu hiển thị/ẩn hồ sơ Gym trên marketplace (UC-018).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGymVisibilityRequest {

    @NotNull(message = "visible is required")
    private Boolean visible;
}
