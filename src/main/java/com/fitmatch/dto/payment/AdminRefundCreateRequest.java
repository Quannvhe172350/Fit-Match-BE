package com.fitmatch.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin/Finance mở yêu cầu hoàn tiền thủ công (UC-055). Yêu cầu luôn bao trùm
 * toàn bộ phần đang giữ của booking; số tiền hoàn thực tế quyết định khi duyệt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminRefundCreateRequest {

    @NotNull(message = "bookingId is required")
    private Long bookingId;

    @NotBlank(message = "Reason is required")
    @Size(max = 500)
    private String reason;
}
