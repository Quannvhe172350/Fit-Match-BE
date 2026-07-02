package com.fitmatch.repository.spec;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Specification cho tìm kiếm Gym trên marketplace (UC-18): chỉ Gym đã APPROVED & active.
 */
public final class GymProfileSpecifications {

    private GymProfileSpecifications() {
    }

    public static Specification<GymProfile> visibleOnMarketplace() {
        return (root, q, cb) -> cb.and(
                cb.equal(root.get("verificationStatus"), VerificationStatus.APPROVED),
                cb.isTrue(root.get("active")));
    }

    public static Specification<GymProfile> keyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("gymName")), like),
                cb.like(cb.lower(root.get("description")), like));
    }

    public static Specification<GymProfile> city(String city) {
        return StringUtils.hasText(city)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("city")), "%" + city.toLowerCase() + "%")
                : null;
    }
}
