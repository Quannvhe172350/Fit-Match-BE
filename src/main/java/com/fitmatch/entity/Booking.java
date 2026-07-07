package com.fitmatch.entity;

import com.fitmatch.common.enums.BookingStatus;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Booking dịch vụ/gói tập (UC-031..045). Gym là bên chịu trách nhiệm xử lý;
 * PT (nếu có) do Gym gán. Giá được chốt snapshot tại thời điểm checkout (UC-034).
 */
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_customer", columnList = "customer_id,status"),
        @Index(name = "idx_bookings_gym_status", columnList = "gym_profile_id,status"),
        @Index(name = "idx_bookings_pt_time", columnList = "pt_profile_id,start_at,end_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_branch_id")
    private GymBranch gymBranch;

    /** Đúng một trong service/package được chốt khi checkout. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_service_id")
    private GymService gymService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_package_id")
    private TrainingPackage trainingPackage;

    /** PT phụ trách — do Gym gán (UC-039); customer có thể đề xuất khi chọn (UC-032). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pt_profile_id")
    private PtProfile ptProfile;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.DRAFT;

    /** Lý do của lần chuyển trạng thái gần nhất (reject/cancel/no-show...). */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    /** Tổng giá trị dịch vụ/gói — snapshot tại checkout (UC-034). */
    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Số tiền phải trả khi checkout (đặt cọc theo booking rules hoặc 100%). */
    @Column(name = "payable_amount", precision = 12, scale = 2)
    private BigDecimal payableAmount;

    /** Hủy muộn (trong cửa sổ mất phí) — dùng cho settlement/dispute (UC-043). */
    @Column(name = "late_cancellation", nullable = false)
    @Builder.Default
    private boolean lateCancellation = false;
}
