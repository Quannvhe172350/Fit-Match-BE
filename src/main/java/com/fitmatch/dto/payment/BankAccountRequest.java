package com.fitmatch.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thêm / sửa tài khoản ngân hàng thụ hưởng (V61). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountRequest {

    @NotNull(message = "Bank is required")
    private Long bankId;

    /**
     * Số tài khoản chỉ gồm chữ và số — QR VietQR nhúng thẳng chuỗi này vào URL,
     * khoảng trắng hay dấu gạch sẽ tạo ra mã quét ra sai tài khoản.
     */
    @NotBlank(message = "Account number is required")
    @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9]+", message = "Account number must contain only letters and digits")
    private String accountNumber;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 150)
    private String accountHolder;

    /** Đặt làm tài khoản mặc định khi tạo lệnh rút. */
    private boolean setDefault;
}
