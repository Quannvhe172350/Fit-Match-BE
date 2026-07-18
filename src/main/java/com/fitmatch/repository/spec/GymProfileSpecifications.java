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

    /**
     * Lọc theo quận/huyện (UC-18/bug 11). Hồ sơ cũ chưa có cột district
     * -> fallback khớp trong address ("123 Nguyễn Trãi, Thanh Xuân").
     */
    public static Specification<GymProfile> district(String district) {
        if (!StringUtils.hasText(district)) {
            return null;
        }
        String like = "%" + district.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("district"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("address"), "")), like));
    }

    /**
     * Lọc theo khoảng giá (bug 11): gym khớp khi có ít nhất một gói tập PUBLISHED
     * có giá trong [min, max].
     */
    public static Specification<GymProfile> packagePriceRange(java.math.BigDecimal minPrice,
                                                              java.math.BigDecimal maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return null;
        }
        return (root, q, cb) -> {
            var sq = q.subquery(Long.class);
            var pkg = sq.from(com.fitmatch.entity.TrainingPackage.class);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(pkg.get("gymProfile").get("id"), root.get("id")));
            predicates.add(cb.equal(pkg.get("status"), com.fitmatch.common.enums.CatalogStatus.PUBLISHED));
            if (minPrice != null) {
                predicates.add(cb.ge(pkg.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.le(pkg.get("price"), maxPrice));
            }
            sq.select(pkg.get("id")).where(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            return cb.exists(sq);
        };
    }
}
