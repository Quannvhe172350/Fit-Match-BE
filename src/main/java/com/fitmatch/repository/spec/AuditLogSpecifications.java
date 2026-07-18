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

    // Bug 12: trước đây so khớp cb.equal phân biệt hoa/thường và bắt gõ đúng
    // 100% ("booking" vs "Booking" ra 0 dòng) — người dùng thấy như "filter
    // không hoạt động". Chuyển sang LIKE không phân biệt hoa/thường.
    public static Specification<AuditLog> hasAction(String action) {
        return StringUtils.hasText(action)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("action")),
                        "%" + action.toLowerCase() + "%")
                : null;
    }

    public static Specification<AuditLog> hasTargetType(String targetType) {
        return StringUtils.hasText(targetType)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("targetType")),
                        "%" + targetType.toLowerCase() + "%")
                : null;
    }

    public static Specification<AuditLog> byActor(String actor) {
        return StringUtils.hasText(actor)
                ? (root, q, cb) -> cb.like(cb.lower(root.get("createdBy")),
                        "%" + actor.toLowerCase() + "%")
                : null;
    }

    public static Specification<AuditLog> createdFrom(LocalDateTime from) {
        return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdTo(LocalDateTime to) {
        return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
