package com.fitmatch.entity;

import com.fitmatch.common.enums.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Gói tập nhiều buổi do Gym cung cấp (UC-025): số buổi, hạn dùng, giá và
 * điều kiện sử dụng. Có thể gắn với một dịch vụ cụ thể (gói PT 10 buổi yoga...)
 * hoặc độc lập (gói thành viên).
 * Schema: training_packages(id, gym_profile_id FK, gym_service_id FK nullable,
 * name, description, price, session_count, validity_days, usage_conditions, active, + audit).
 */
@Entity
@Table(name = "training_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingPackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    /** Dịch vụ áp dụng (nullable — gói không gắn dịch vụ cụ thể). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_service_id")
    private GymService gymService;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Bug S2-05: phụ phí khi khách CHỌN PT cho gói. {@code price} là giá không kèm
     * PT; null/0 = gym không tính thêm. Xem {@link GymService#getPtSurcharge()}.
     */
    @Column(name = "pt_surcharge", precision = 12, scale = 2)
    private BigDecimal ptSurcharge;

    /** Số buổi trong gói. */
    @Column(name = "session_count", nullable = false)
    private Integer sessionCount;

    /** Hạn sử dụng tính theo ngày kể từ khi kích hoạt; null = không giới hạn. */
    @Column(name = "validity_days")
    private Integer validityDays;

    /** Điều kiện sử dụng gói (UC-025). */
    @Column(name = "usage_conditions", length = 1000)
    private String usageConditions;

    /** Quy tắc thanh toán/đặt lịch (UC-026); null = mặc định nền tảng. */
    @Embedded
    private BookingRules bookingRules;

    /** Vòng đời hiển thị trên marketplace (UC-027). {@code active} được đồng bộ = PUBLISHED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CatalogStatus status = CatalogStatus.PUBLISHED;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
