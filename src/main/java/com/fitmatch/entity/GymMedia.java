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
 * Ảnh/media công khai của Gym hoặc chi nhánh (UC-016). File biểu diễn bằng URL
 * (upload qua /api/files/upload trước).
 * Schema: gym_media(id, gym_profile_id FK, gym_branch_id FK nullable, url, caption, + audit).
 */
@Entity
@Table(name = "gym_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymMedia extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    /** Ảnh của chi nhánh cụ thể; null = ảnh chung của Gym. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_branch_id")
    private GymBranch gymBranch;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 255)
    private String caption;
}
