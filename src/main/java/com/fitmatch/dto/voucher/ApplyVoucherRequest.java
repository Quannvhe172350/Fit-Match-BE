package com.fitmatch.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khách áp mã voucher vào booking nháp (UC-073). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplyVoucherRequest {

    @NotBlank(message = "code is required")
    private String code;
}
