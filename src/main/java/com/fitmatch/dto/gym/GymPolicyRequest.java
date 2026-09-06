package com.fitmatch.dto.gym;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Upsert chính sách vận hành của Gym (UC-017).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPolicyRequest {

    @Size(max = 2000)
    private String bookingPolicy;

    @Size(max = 2000)
    private String cancellationPolicy;

    @Size(max = 2000)
    private String noShowPolicy;

    @Size(max = 2000)
    private String houseRules;

    /**
     * V94 — luật huỷ ngày tập ở dạng máy tính được. {@code cancellationPolicy} ở
     * trên vẫn là văn bản cho người đọc; ba trường này mới quyết định số tiền.
     *
     * <p>Bỏ trống = giữ nguyên giá trị đang có (hoặc mặc định 24h/12h/50% cho gym
     * chưa từng cấu hình). Không dùng null để nghĩa là "0 giờ" — đó sẽ là một
     * chính sách khắc nghiệt đặt ra do sơ ý.
     */
    @Min(value = 0, message = "cancelFullRefundHours must be >= 0")
    @Max(value = 720, message = "cancelFullRefundHours must be <= 720")
    private Integer cancelFullRefundHours;

    @Min(value = 0, message = "cancelPartialRefundHours must be >= 0")
    @Max(value = 720, message = "cancelPartialRefundHours must be <= 720")
    private Integer cancelPartialRefundHours;

    @DecimalMin(value = "0.00", message = "cancelPartialRefundPercent must be >= 0")
    @DecimalMax(value = "100.00", message = "cancelPartialRefundPercent must be <= 100")
    private java.math.BigDecimal cancelPartialRefundPercent;
}
