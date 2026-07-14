package com.fitmatch.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Gym gửi yêu cầu rút tiền từ available balance (UC-062). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalCreateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Bank account number is required")
    @Size(max = 50)
    private String bankAccount;

    @NotBlank(message = "Bank name is required")
    @Size(max = 100)
    private String bankName;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 150)
    private String accountHolder;
}
