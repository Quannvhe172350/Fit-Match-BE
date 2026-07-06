package com.fitmatch.dto.pt;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tạo khoảng thời gian không nhận đặt lịch (UC-029).
 * Với Gym: truyền đúng MỘT trong ptId/branchId; với PT tự tạo: bỏ trống cả hai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedTimeRequest {

    private Long ptId;

    private Long branchId;

    @NotNull(message = "startAt is required")
    private LocalDateTime startAt;

    @NotNull(message = "endAt is required")
    private LocalDateTime endAt;

    @Size(max = 255)
    private String reason;
}
