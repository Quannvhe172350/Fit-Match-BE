package com.fitmatch.dto.gym;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tạo/cập nhật gói tập (UC-025).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPackageRequest {

    @NotBlank(message = "Package name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal price;

    /** Bug S2-05: phụ phí khi khách chọn PT (null/0 = không tính thêm). */
    @DecimalMin(value = "0.0", message = "PT surcharge must be >= 0")
    private BigDecimal ptSurcharge;

    @NotNull(message = "Session count is required")
    @Positive(message = "Session count must be positive")
    private Integer sessionCount;

    @Positive(message = "Validity days must be positive")
    private Integer validityDays;

    @Size(max = 1000)
    private String usageConditions;

    /** Dịch vụ áp dụng (nullable). */
    private Long gymServiceId;
}
