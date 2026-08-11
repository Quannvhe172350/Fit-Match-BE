package com.fitmatch.service.support;

import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tổng hợp điểm đánh giá VISIBLE (UC-071) cho marketplace/monitor. Truy vấn AVG
 * + COUNT trực tiếp ở DB (không load review) — tránh N+1 khi liệt kê.
 */
@Component
@RequiredArgsConstructor
public class RatingAggregator {

    private final ReviewRepository reviewRepository;
    private final GymProfileRepository gymProfileRepository;
    private final PtProfileRepository ptProfileRepository;

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

    /** Phổ điểm {sao -> số lượng} của gym; sao không có review nào thì vắng khỏi map. */
    public Map<Integer, Long> distributionForGym(Long gymProfileId) {
        return toDistribution(reviewRepository.ratingDistributionGym(gymProfileId));
    }

    public Map<Integer, Long> distributionForPt(Long ptProfileId) {
        return toDistribution(reviewRepository.ratingDistributionPt(ptProfileId));
    }

    private Map<Integer, Long> toDistribution(List<Object[]> rows) {
        Map<Integer, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    // ----- UC-008 (V51): đồng bộ cột denorm avg_rating/rating_count để sort marketplace -----

    /** Gọi sau mỗi thay đổi review liên quan gym (create/update/delete/moderate). */
    public void refreshGym(Long gymProfileId) {
        if (gymProfileId == null) return;
        Rating r = forGym(gymProfileId);
        gymProfileRepository.findById(gymProfileId).ifPresent(g -> {
            g.setAvgRating(r.average());
            g.setRatingCount((int) r.count());
            gymProfileRepository.save(g);
        });
    }

    /** Gọi sau mỗi thay đổi review liên quan PT. */
    public void refreshPt(Long ptProfileId) {
        if (ptProfileId == null) return;
        Rating r = forPt(ptProfileId);
        ptProfileRepository.findById(ptProfileId).ifPresent(p -> {
            p.setAvgRating(r.average());
            p.setRatingCount((int) r.count());
            ptProfileRepository.save(p);
        });
    }
}
