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
 * Hồ sơ Gym (UC-41..46). Quan hệ 1-1 với {@link User}.
 * User nộp hồ sơ -> PENDING; Admin duyệt -> APPROVED + nâng role ROLE_GYM_OPERATOR (D-09).
 * Schema (Hibernate ddl-auto): gym_profiles(id, user_id FK UNIQUE, gym_name, description,
 * address, city, phone, verification_status, rejection_reason, active, + audit).
 */
@Entity
@Table(name = "gym_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "gym_name", nullable = false, length = 150)
    private String gymName;

    @Column(length = 2000)
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Ghi chú của Admin khi yêu cầu bổ sung hồ sơ (UC-013). */
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    /**
     * Optimistic lock (P1 batch 2): chống hai admin cùng xử lý một hồ sơ (approve
     * vs reject) ghi đè quyết định của nhau. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
