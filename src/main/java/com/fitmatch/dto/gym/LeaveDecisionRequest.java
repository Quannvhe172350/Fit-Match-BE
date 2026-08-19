package com.fitmatch.dto.gym;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lý do từ chối đơn nghỉ — bắt buộc, PT phải biết vì sao để còn sắp xếp lại. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDecisionRequest {

    @NotBlank(message = "reason is required")
    @Size(min = 5, max = 1000, message = "reason must be 5..1000 characters")
    private String reason;
}
