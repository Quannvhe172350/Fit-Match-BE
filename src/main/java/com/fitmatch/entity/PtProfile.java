package com.fitmatch.entity;

import com.fitmatch.common.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hồ sơ Personal Trainer (UC-23..30). Quan hệ 1-1 với {@link User}.
 * Một user (ban đầu ROLE_CUSTOMER) nộp hồ sơ -> PENDING; Admin duyệt -> APPROVED + nâng role ROLE_PT.
 * Schema (Hibernate ddl-auto): pt_profiles(id, user_id FK UNIQUE, display_name, bio, service_area,
 * specialization, experience_years, hourly_rate, verification_status, rejection_reason, active, + audit).
 */
@Entity
@Table(name = "pt_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 2000)
    private String bio;

    @Column(name = "service_area", length = 255)
    private String serviceArea;

    @Column(length = 255)
    private String specialization;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Hiển thị trên marketplace hay không (chỉ true khi APPROVED và PT không tự ẩn). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;
}
