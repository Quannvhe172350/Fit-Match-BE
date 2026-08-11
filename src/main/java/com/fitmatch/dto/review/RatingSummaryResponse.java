package com.fitmatch.dto.review;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tổng hợp điểm đánh giá của một gym/PT (UC-009/071).
 *
 * <p>Chỉ tính review đang VISIBLE: review bị gỡ (REMOVED) hoặc bị ẩn khi kiểm duyệt
 * (HIDDEN) không được kéo điểm trung bình của đối tác xuống.
 */
@Getter
@Builder
public class RatingSummaryResponse {

    private String targetType;
    private Long targetId;
    private BigDecimal averageRating;
    private long totalReviews;
    private long rating1Count;
    private long rating2Count;
    private long rating3Count;
    private long rating4Count;
    private long rating5Count;

    public static RatingSummaryResponse of(String targetType, Long targetId,
                                           BigDecimal average, long total,
                                           Map<Integer, Long> distribution) {
        return RatingSummaryResponse.builder()
                .targetType(targetType)
                .targetId(targetId)
                .averageRating(average)
                .totalReviews(total)
                .rating1Count(distribution.getOrDefault(1, 0L))
                .rating2Count(distribution.getOrDefault(2, 0L))
                .rating3Count(distribution.getOrDefault(3, 0L))
                .rating4Count(distribution.getOrDefault(4, 0L))
                .rating5Count(distribution.getOrDefault(5, 0L))
                .build();
    }
}
