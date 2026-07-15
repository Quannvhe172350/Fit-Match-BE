package com.fitmatch.dto.loyalty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khách dùng điểm thưởng cho booking nháp (UC-073). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplyLoyaltyRequest {

    @NotNull(message = "points is required")
    @Positive(message = "points must be positive")
    private Integer points;
}
