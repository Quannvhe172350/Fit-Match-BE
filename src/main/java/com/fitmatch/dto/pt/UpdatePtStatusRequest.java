package com.fitmatch.dto.pt;

import com.fitmatch.common.enums.PtStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gym bật/tắt PT (UC-021). Chỉ chấp nhận ACTIVE hoặc INACTIVE;
 * SUSPENDED là quyền của Admin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePtStatusRequest {

    @NotNull(message = "status is required")
    private PtStatus status;
}
