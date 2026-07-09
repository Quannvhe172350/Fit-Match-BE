package com.fitmatch.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Cập nhật cấu hình kinh tế nền tảng (UC-072).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionConfigRequest {

    @NotNull(message = "commissionPercent is required")
    @DecimalMin(value = "0.0", message = "commissionPercent must be 0-100")
    @DecimalMax(value = "100.0", message = "commissionPercent must be 0-100")
    private BigDecimal commissionPercent;

    @NotNull(message = "platformFeePercent is required")
    @DecimalMin(value = "0.0", message = "platformFeePercent must be 0-100")
    @DecimalMax(value = "100.0", message = "platformFeePercent must be 0-100")
    private BigDecimal platformFeePercent;

    @NotNull(message = "settlementHoldDays is required")
    @Min(value = 0, message = "settlementHoldDays must be >= 0")
    private Integer settlementHoldDays;
}
