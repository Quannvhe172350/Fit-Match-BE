package com.fitmatch.repository.spec;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Specification cho tìm kiếm PT trên marketplace (UC-14): chỉ PT đã APPROVED & active.
 */
public final class PtProfileSpecifications {

    private PtProfileSpecifications() {
    }

    /** Chỉ hiển thị PT đã được duyệt và đang bật hiển thị. */
    public static Specification<PtProfile> visibleOnMarketplace() {
        return (root, q, cb) -> cb.and(
                cb.equal(root.get("verificationStatus"), VerificationStatus.APPROVED),
                cb.isTrue(root.get("active")));
    }

    public static Specification<PtProfile> keyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("displayName")), like),
                cb.like(cb.lower(root.get("specialization")), like),
                cb.like(cb.lower(root.get("bio")), like));
    }

    public static Specification<PtProfile> specialization(String specialization) {
        return StringUtils.hasText(specialization)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("specialization")), "%" + specialization.toLowerCase() + "%")
                : null;
    }

    public static Specification<PtProfile> serviceArea(String area) {
        return StringUtils.hasText(area)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("serviceArea")), "%" + area.toLowerCase() + "%")
                : null;
    }
}
