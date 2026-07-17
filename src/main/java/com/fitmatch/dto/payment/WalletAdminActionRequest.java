package com.fitmatch.dto.payment;

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

/** UC-060: admin/finance đóng băng / gỡ đóng băng một phần số dư ví gym. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAdminActionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    @Size(max = 500)
    private String reason;
}
