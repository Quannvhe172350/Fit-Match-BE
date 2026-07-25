package com.fitmatch.dto.measurement;

import com.fitmatch.entity.BodyMeasurement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BodyMeasurementResponse {

    private Long id;
    private LocalDate measuredAt;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal bodyFatPercent;
    private BigDecimal chestCm;
    private BigDecimal waistCm;
    private BigDecimal hipCm;
    private String note;
    /** BMI = kg / m² — tính sẵn khi có đủ cân nặng + chiều cao. */
    private BigDecimal bmi;
    private LocalDateTime createdAt;

    public static BodyMeasurementResponse of(BodyMeasurement m) {
        return BodyMeasurementResponse.builder()
                .id(m.getId())
                .measuredAt(m.getMeasuredAt())
                .weightKg(m.getWeightKg())
                .heightCm(m.getHeightCm())
                .bodyFatPercent(m.getBodyFatPercent())
                .chestCm(m.getChestCm())
                .waistCm(m.getWaistCm())
                .hipCm(m.getHipCm())
                .note(m.getNote())
                .bmi(computeBmi(m.getWeightKg(), m.getHeightCm()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private static BigDecimal computeBmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }
}
