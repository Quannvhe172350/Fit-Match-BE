package com.fitmatch.dto.dispute;

import com.fitmatch.common.enums.DisputeResolution;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Quyết định giải quyết tranh chấp (UC-066) + áp dụng tài chính (UC-067).
 * refundAmount bắt buộc cho REFUND_PARTIAL/SPLIT (số hoàn cho khách, phần còn
 * lại về Gym); bỏ qua với REFUND_FULL/RELEASE_TO_GYM/NO_ACTION/PENALTY.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "resolution is required")
    private DisputeResolution resolution;

    @DecimalMin(value = "0.01", message = "refundAmount must be positive")
    private BigDecimal refundAmount;

    @Size(max = 1000)
    private String note;
}
