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
 * Danh sách chờ khi slot mong muốn không còn (UC-044). Customer đăng ký chờ
 * cho một dịch vụ hoặc gói tập kèm khung giờ mong muốn; Gym xem để chủ động
 * liên hệ khi có chỗ trống.
 * Schema: waitlist_entries(id, customer_id FK, gym_service_id FK?,
 * training_package_id FK?, preferred_start, note, active, + audit).
 */
@Entity
@Table(name = "waitlist_entries", indexes = {
        @Index(name = "idx_waitlist_service", columnList = "gym_service_id,active"),
        @Index(name = "idx_waitlist_package", columnList = "training_package_id,active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_service_id")
    private GymService gymService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_package_id")
    private TrainingPackage trainingPackage;

    @Column(name = "preferred_start")
    private LocalDateTime preferredStart;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
