package com.fitmatch.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Kiểm tra khả dụng của PT và/hoặc chi nhánh cho một khung giờ (UC-030).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityCheckRequest {

    /** PT muốn đặt (nullable nếu chỉ kiểm tra chi nhánh). */
    private Long ptId;

    /** Chi nhánh muốn đặt (nullable nếu chỉ kiểm tra PT). */
    private Long branchId;

    @NotNull(message = "startAt is required")
    private LocalDateTime startAt;

    @NotNull(message = "endAt is required")
    private LocalDateTime endAt;
}
