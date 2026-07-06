package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gán PT vào chi nhánh / dịch vụ / gói tập của Gym (UC-022).
 * Mỗi dòng là một liên kết: PT + đúng MỘT trong ba đích (branch/service/package)
 * — service layer đảm bảo ràng buộc này.
 * Schema: pt_assignments(id, pt_profile_id FK, gym_branch_id FK?, gym_service_id FK?,
 * training_package_id FK?, active, + audit) + unique theo từng cặp.
 */
@Entity
@Table(name = "pt_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pt_assignment_branch", columnNames = {"pt_profile_id", "gym_branch_id"}),
        @UniqueConstraint(name = "uk_pt_assignment_service", columnNames = {"pt_profile_id", "gym_service_id"}),
        @UniqueConstraint(name = "uk_pt_assignment_package", columnNames = {"pt_profile_id", "training_package_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_branch_id")
    private GymBranch gymBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_service_id")
    private GymService gymService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_package_id")
    private TrainingPackage trainingPackage;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
