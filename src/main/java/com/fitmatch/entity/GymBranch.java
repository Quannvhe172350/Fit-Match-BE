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
 * Chi nhánh của Gym (UC-50..52).
 * Schema: gym_branches(id, gym_profile_id FK, name, address, city, phone, active, + audit).
 */
@Entity
@Table(name = "gym_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymBranch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 30)
    private String phone;

    /** Tiện ích của chi nhánh (UC-016), danh sách phân tách bằng dấu phẩy. */
    @Column(length = 1000)
    private String amenities;

    /** Sức chứa tối đa của chi nhánh (UC-017); null = không giới hạn. */
    @Column
    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
