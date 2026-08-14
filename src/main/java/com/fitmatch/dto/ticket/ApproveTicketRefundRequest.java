package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.RefundMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Câu 11: admin chọn cách hoàn. KHÔNG có ô nhập số tiền tự do — số tiền do
 * PartialRefundCalculator quyết để bất biến "refund + retained = payable" không
 * bao giờ bị người dùng gõ sai làm vỡ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveTicketRefundRequest {

    @NotNull(message = "mode is required")
    private RefundMode mode;

    @Size(max = 500)
    private String note;
}
