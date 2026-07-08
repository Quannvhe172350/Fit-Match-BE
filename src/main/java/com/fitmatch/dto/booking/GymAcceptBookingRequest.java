package com.fitmatch.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gym nhận booking (UC-038), kèm gán PT phụ trách nếu cần (UC-039).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymAcceptBookingRequest {

    /** PT phụ trách; null = giữ PT khách đề xuất (nếu có) hoặc không cần PT. */
    private Long ptId;
}
