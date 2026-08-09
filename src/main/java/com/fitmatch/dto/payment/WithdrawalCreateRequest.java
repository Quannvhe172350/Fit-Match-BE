package com.fitmatch.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Gym / PT / khách hàng gửi yêu cầu rút tiền từ available balance (UC-062).
 * <p>
 * V61: tài khoản thụ hưởng chọn từ danh sách đã lưu ({@code bank_accounts}) thay
 * vì gõ tay mỗi lần. Gõ tay không kèm được mã BIN nên không dựng được QR cho
 * admin quét, và mỗi lần gõ lại là một cơ hội sai số tài khoản.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalCreateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1", message = "Amount must be at least 1")
    private BigDecimal amount;

    /** Tài khoản ngân hàng thụ hưởng — phải thuộc chính người gửi yêu cầu. */
    @NotNull(message = "Bank account is required")
    private Long bankAccountId;
}
