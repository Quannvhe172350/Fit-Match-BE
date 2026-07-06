package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Khoảng thời gian không nhận đặt lịch (UC-029): nghỉ lễ, bảo trì (chi nhánh)
 * hoặc bận cá nhân (PT). Đúng MỘT trong ptProfile/gymBranch khác null —
 * service layer đảm bảo.
 * Schema: blocked_times(id, pt_profile_id FK?, gym_branch_id FK?, start_at,
 * end_at, reason, + audit).
 */
@Entity
@Table(name = "blocked_times", indexes = {
        @Index(name = "idx_blocked_pt_time", columnList = "pt_profile_id,start_at,end_at"),
        @Index(name = "idx_blocked_branch_time", columnList = "gym_branch_id,start_at,end_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedTime extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pt_profile_id")
    private PtProfile ptProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_branch_id")
    private GymBranch gymBranch;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(length = 255)
    private String reason;
}
