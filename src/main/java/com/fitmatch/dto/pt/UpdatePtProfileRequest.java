package com.fitmatch.dto.pt;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cập nhật một phần hồ sơ PT (UC-26). Trường null = giữ nguyên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePtProfileRequest {

    @Size(max = 120)
    private String displayName;

    @Size(max = 2000)
    private String bio;

    @Size(max = 255)
    private String serviceArea;

    @Size(max = 255)
    private String specialization;

    @PositiveOrZero(message = "Experience years must be >= 0")
    private Integer experienceYears;
}
