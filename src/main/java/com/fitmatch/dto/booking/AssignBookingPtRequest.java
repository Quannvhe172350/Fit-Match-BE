package com.fitmatch.dto.booking;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gán/đổi PT phụ trách booking (UC-039).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignBookingPtRequest {

    @NotNull(message = "ptId is required")
    private Long ptId;
}
