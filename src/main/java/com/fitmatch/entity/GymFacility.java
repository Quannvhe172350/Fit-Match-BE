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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cơ sở vật chất của Gym (UC-47..49), vd phòng tạ, hồ bơi, sauna.
 * Schema: gym_facilities(id, gym_profile_id FK, name, description, active, + audit).
 */
@Entity
@Table(name = "gym_facilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymFacility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    /** Chi nhánh chứa cơ sở vật chất (UC-016); nullable cho dữ liệu cũ chưa gắn branch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_branch_id")
    private GymBranch gymBranch;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
