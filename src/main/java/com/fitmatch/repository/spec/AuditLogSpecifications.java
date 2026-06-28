package com.fitmatch.repository.spec;

import com.fitmatch.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Specification dùng cho tìm kiếm/lọc audit logs của Admin (UC-13).
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> hasAction(String action) {
        return StringUtils.hasText(action)
                ? (root, q, cb) -> cb.equal(root.get("action"), action) : null;
    }

    public static Specification<AuditLog> hasTargetType(String targetType) {
        return StringUtils.hasText(targetType)
                ? (root, q, cb) -> cb.equal(root.get("targetType"), targetType) : null;
    }

    public static Specification<AuditLog> byActor(String actor) {
        return StringUtils.hasText(actor)
                ? (root, q, cb) -> cb.equal(root.get("createdBy"), actor) : null;
    }

    public static Specification<AuditLog> createdFrom(LocalDateTime from) {
        return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdTo(LocalDateTime to) {
        return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
