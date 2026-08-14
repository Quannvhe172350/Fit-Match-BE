package com.fitmatch.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Đổi khung giờ PT trong cùng ngày, hoặc bổ sung PT cho một ngày đang trống của
 * vé gói (câu 34). Không đổi được ngày qua endpoint này.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionPtRequest {

    @NotNull(message = "ptId is required")
    private Long ptId;

    @NotNull(message = "slotStart is required")
    private LocalTime slotStart;
}
