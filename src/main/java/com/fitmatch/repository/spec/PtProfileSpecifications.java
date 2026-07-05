package com.fitmatch.repository.spec;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Specification cho tìm kiếm PT trên marketplace (UC-008/UC-021):
 * PT ACTIVE thuộc Gym đã APPROVED và đang hiển thị.
 */
public final class PtProfileSpecifications {

    private PtProfileSpecifications() {
    }

    /**
     * UC-021: PT hiển thị khi chính PT đang ACTIVE và Gym chịu trách nhiệm đã APPROVED
     * + đang hiển thị. Inner join loại luôn hồ sơ PT self-registered cũ (không có Gym).
     */
    public static Specification<PtProfile> visibleOnMarketplace() {
        return (root, q, cb) -> {
            var gym = root.join("gymProfile");
            return cb.and(
                    cb.equal(root.get("status"), PtStatus.ACTIVE),
                    cb.equal(gym.get("verificationStatus"), VerificationStatus.APPROVED),
                    cb.isTrue(gym.get("active")));
        };
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
