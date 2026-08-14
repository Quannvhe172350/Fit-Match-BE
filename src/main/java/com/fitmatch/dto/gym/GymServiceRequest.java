package com.fitmatch.dto.gym;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    /** Giá cộng một lần cho cả vé — không nhân theo số ngày. */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal price;
}
