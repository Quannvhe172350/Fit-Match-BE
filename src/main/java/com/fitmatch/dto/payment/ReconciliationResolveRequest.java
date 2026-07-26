package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.ReconStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UC-056: tất toán một giao dịch bất thường mà không gắn vào booking.
 * Ghi chú là bắt buộc — đây là quyết định tài chính, phải truy được lý do.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResolveRequest {

    /** Chỉ nhận RESOLVED_REFUNDED (đã chuyển trả người gửi) hoặc RESOLVED_IGNORED. */
    @NotNull(message = "Resolution is required")
    private ReconStatus resolution;

    @NotBlank(message = "Resolution note is required")
    @Size(max = 500)
    private String note;
}
