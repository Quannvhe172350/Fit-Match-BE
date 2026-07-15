package com.fitmatch.service.support;

import com.fitmatch.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tổng hợp điểm đánh giá VISIBLE (UC-071) cho marketplace/monitor. Truy vấn AVG
 * + COUNT trực tiếp ở DB (không load review) — tránh N+1 khi liệt kê.
 */
@Component
@RequiredArgsConstructor
public class RatingAggregator {

    private final ReviewRepository reviewRepository;

    public record Rating(BigDecimal average, long count) {
        static Rating from(Object[] row) {
            // JPQL avg trả về Double; count trả về Long.
            Object[] r = row.length == 1 && row[0] instanceof Object[] nested ? nested : row;
            double avg = r[0] != null ? ((Number) r[0]).doubleValue() : 0d;
            long cnt = r[1] != null ? ((Number) r[1]).longValue() : 0L;
            return new Rating(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP), cnt);
        }
    }

    public Rating forGym(Long gymProfileId) {
        return Rating.from(reviewRepository.aggregateGym(gymProfileId));
    }

    public Rating forPt(Long ptProfileId) {
        return Rating.from(reviewRepository.aggregatePt(ptProfileId));
    }
}
