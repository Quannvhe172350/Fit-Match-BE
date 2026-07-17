package com.fitmatch.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** UC-062 (D-11): xác nhận chi trả — bắt buộc mã giao dịch chuyển khoản để đối soát. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkPaidRequest {

    @NotBlank(message = "Payout reference is required")
    @Size(max = 100)
    private String payoutReference;

    @Size(max = 500)
    private String note;
}
