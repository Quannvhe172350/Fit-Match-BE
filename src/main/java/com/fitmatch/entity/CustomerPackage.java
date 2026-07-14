package com.fitmatch.entity;

import com.fitmatch.common.enums.CustomerPackageStatus;
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
 * Gói tập khách đã mua (UC-049/051): tạo khi booking mua gói hoàn tất buổi đầu;
 * các buổi sau đặt miễn phí và trừ dần sessionsUsed. Hết buổi -> EXHAUSTED;
 * quá validityDays -> EXPIRED.
 * Schema: customer_packages(id, customer_id FK, training_package_id FK,
 * purchase_booking_id FK UNIQUE, sessions_total, sessions_used, expires_at,
 * status, + audit).
 */
@Entity
@Table(name = "customer_packages", indexes =
        @Index(name = "idx_customer_packages_owner", columnList = "customer_id,status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_package_id", nullable = false)
    private TrainingPackage trainingPackage;

    /** Booking mua gói (buổi đầu tiên) — nguồn tiền của cả gói. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_booking_id", nullable = false, unique = true)
    private Booking purchaseBooking;

    @Column(name = "sessions_total", nullable = false)
    private int sessionsTotal;

    @Column(name = "sessions_used", nullable = false)
    @Builder.Default
    private int sessionsUsed = 0;

    /** Hạn dùng = thời điểm kích hoạt + validityDays; null = không giới hạn. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerPackageStatus status = CustomerPackageStatus.ACTIVE;
}
