package com.fitmatch.repository.spec;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.entity.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Specification dùng cho tìm kiếm/lọc user của Admin (UC-10).
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Khớp keyword với username hoặc email (LIKE, không phân biệt hoa thường). */
    public static Specification<User> keyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("username")), like),
                cb.like(cb.lower(root.get("email")), like));
    }

    public static Specification<User> hasRole(Role role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
