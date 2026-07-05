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

import java.math.BigDecimal;

/**
 * Dịch vụ do Gym cung cấp (UC-53..55), vd gói tập tháng, lớp yoga.
 * Schema: gym_services(id, gym_profile_id FK, name, description, price, duration_minutes, active, + audit).
 */
@Entity
@Table(name = "gym_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymService extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Danh mục dịch vụ từ master data (UC-024/UC-078); nullable cho dịch vụ cũ. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    /** Điều kiện tham gia / đối tượng phù hợp (UC-024). */
    @Column(name = "eligibility_notes", length = 1000)
    private String eligibilityNotes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
