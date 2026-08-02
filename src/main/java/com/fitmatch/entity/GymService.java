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

    /**
     * Bug S2-05: phụ phí khi khách CHỌN PT. {@code price} là giá tự tập (không PT);
     * khách chọn PT thì trả price + ptSurcharge. Mỗi gym tự quyết mức phụ phí này —
     * null/0 nghĩa là gym không tính thêm tiền cho PT.
     */
    @Column(name = "pt_surcharge", precision = 12, scale = 2)
    private BigDecimal ptSurcharge;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

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
