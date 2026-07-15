package com.fitmatch.entity;

import com.fitmatch.common.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Đánh giá sau buổi tập (UC-069). Một booking COMPLETED có tối đa một review;
 * ngữ cảnh Gym/dịch vụ/PT lấy từ booking. Gym được phản hồi một lần (UC-069).
 * Điểm chỉ tính vào trung bình khi status = VISIBLE (UC-071).
 * Schema: reviews(id, booking_id FK UNIQUE, customer_id FK, gym_profile_id FK,
 * gym_service_id?, training_package_id?, pt_profile_id?, rating, comment, status,
 * reply, replied_by, replied_at, + audit).
 */
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_gym", columnList = "gym_profile_id,status"),
        @Index(name = "idx_reviews_pt", columnList = "pt_profile_id,status"),
        @Index(name = "idx_reviews_customer", columnList = "customer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_service_id")
    private GymService gymService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_package_id")
    private TrainingPackage trainingPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pt_profile_id")
    private PtProfile ptProfile;

    /** 1..5 sao. */
    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.VISIBLE;

    /** Phản hồi của Gym (UC-069). */
    @Column(length = 2000)
    private String reply;

    @Column(name = "replied_by")
    private String repliedBy;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;
}
