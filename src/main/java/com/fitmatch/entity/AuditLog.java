package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản ghi nhật ký audit cho các hành động nhạy cảm (UC-13).
 * Actor & thời điểm lấy từ BaseEntity (created_by, created_at).
 * Schema (Hibernate ddl-auto): audit_logs(id, action, target_type, target_id, description, + audit).
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_target", columnList = "target_type,target_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã hành động, vd USER_LOCK, USER_UNLOCK, USER_ROLE_ASSIGN. */
    @Column(nullable = false, length = 64)
    private String action;

    /** Loại đối tượng bị tác động, vd "User". */
    @Column(name = "target_type", length = 64)
    private String targetType;

    /** Khoá đối tượng bị tác động (dạng chuỗi để tổng quát). */
    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(length = 1000)
    private String description;
}
