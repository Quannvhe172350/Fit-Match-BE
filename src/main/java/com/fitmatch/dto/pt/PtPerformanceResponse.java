package com.fitmatch.dto.pt;

import java.math.BigDecimal;

/**
 * UC-023 (P1-18): tổng hợp hiệu suất/chất lượng của một PT cho Gym/Admin —
 * điểm đánh giá, số buổi hoàn tất/hủy/vắng mặt, và số tranh chấp liên quan.
 */
public record PtPerformanceResponse(
        Long ptId,
        String displayName,
        BigDecimal averageRating,
        long reviewCount,
        long completedBookings,
        long cancelledBookings,
        long noShowBookings,
        long disputes
) {
}
