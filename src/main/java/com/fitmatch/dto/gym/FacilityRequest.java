package com.fitmatch.dto.gym;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạo/cập nhật cơ sở vật chất (UC-47/48).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityRequest {

    @NotBlank(message = "Facility name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;
}
