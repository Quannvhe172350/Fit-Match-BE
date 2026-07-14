package com.fitmatch.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Quyết định hoàn tiền (UC-056). approvedAmount bỏ trống = duyệt toàn bộ;
 * duyệt một phần thì phần còn lại chuyển sang pending settlement cho Gym (phí giữ lại).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefundDecisionRequest {

    @DecimalMin(value = "0.00", message = "Approved amount cannot be negative")
    private BigDecimal approvedAmount;

    @Size(max = 500)
    private String note;
}
