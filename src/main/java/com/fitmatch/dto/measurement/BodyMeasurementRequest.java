package com.fitmatch.dto.measurement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Khách hàng ghi nhận số đo cơ thể (UC-051). Mọi chỉ số đều tùy chọn —
 * chỉ ngày đo là bắt buộc; khoảng giá trị chặn dữ liệu vô nghĩa.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BodyMeasurementRequest {

    @NotNull(message = "Measured date is required")
    @PastOrPresent(message = "Measured date cannot be in the future")
    private LocalDate measuredAt;

    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
    @DecimalMax(value = "400.0", message = "Weight must be at most 400 kg")
    private BigDecimal weightKg;

    @DecimalMin(value = "80.0", message = "Height must be at least 80 cm")
    @DecimalMax(value = "250.0", message = "Height must be at most 250 cm")
    private BigDecimal heightCm;

    @DecimalMin(value = "1.0", message = "Body fat must be at least 1%")
    @DecimalMax(value = "70.0", message = "Body fat must be at most 70%")
    private BigDecimal bodyFatPercent;

    @DecimalMin(value = "30.0", message = "Chest must be at least 30 cm")
    @DecimalMax(value = "250.0", message = "Chest must be at most 250 cm")
    private BigDecimal chestCm;

    @DecimalMin(value = "30.0", message = "Waist must be at least 30 cm")
    @DecimalMax(value = "250.0", message = "Waist must be at most 250 cm")
    private BigDecimal waistCm;

    @DecimalMin(value = "30.0", message = "Hip must be at least 30 cm")
    @DecimalMax(value = "250.0", message = "Hip must be at most 250 cm")
    private BigDecimal hipCm;

    @Size(max = 500)
    private String note;
}
