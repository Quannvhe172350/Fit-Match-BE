package com.fitmatch.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer/Gym mở yêu cầu hoàn tiền cho booking (UC-055). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefundReasonRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 500)
    private String reason;
}
