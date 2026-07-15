package com.fitmatch.dto.voucher;

import com.fitmatch.common.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Admin tạo/cập nhật voucher (UC-073). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRequest {

    @NotBlank(message = "code is required")
    @Size(max = 40)
    private String code;

    @Size(max = 255)
    private String description;

    @NotNull(message = "discountType is required")
    private DiscountType discountType;

    @NotNull(message = "discountValue is required")
    @DecimalMin(value = "0.01", message = "discountValue must be positive")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.00")
    private BigDecimal minBookingAmount;

    @DecimalMin(value = "0.01")
    private BigDecimal maxDiscount;

    @Positive(message = "usageLimit must be positive")
    private Integer usageLimit;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean active;
}
