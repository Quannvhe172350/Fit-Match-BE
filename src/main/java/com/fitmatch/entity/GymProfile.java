package com.fitmatch.entity;

import com.fitmatch.common.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hồ sơ Gym (UC-41..46). Quan hệ 1-1 với {@link User}.
 * User nộp hồ sơ -> PENDING; Admin duyệt -> APPROVED + nâng role ROLE_GYM_OPERATOR (D-09).
 * Schema (Hibernate ddl-auto): gym_profiles(id, user_id FK UNIQUE, gym_name, description,
 * address, city, phone, verification_status, rejection_reason, active, + audit).
 */
@Entity
@Table(name = "gym_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "gym_name", nullable = false, length = 150)
    private String gymName;

    @Column(length = 2000)
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    /** Quận/huyện — phục vụ bộ lọc vị trí theo thành phố + quận (UC-18). */
    @Column(length = 100)
    private String district;

    /**
     * UC-18 (V55): toạ độ trụ sở phục vụ tìm kiếm theo bán kính. Lấy từ Google
     * Geocoding API khi lưu địa chỉ, hoặc do operator chọn trực tiếp trên bản đồ.
     * null = chưa geocode được -> gym không xuất hiện trong kết quả tìm quanh đây.
     */
    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    /** place_id của Google — định danh ổn định của địa điểm, dùng để tra cứu lại. */
    @Column(name = "place_id", length = 255)
    private String placeId;

    /** Địa chỉ đã chuẩn hoá do Google trả về (hiển thị trên bản đồ/InfoWindow). */
    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    /** Thời điểm geocode gần nhất — null nghĩa là chưa từng geocode thành công. */
    @Column(name = "geocoded_at")
    private java.time.LocalDateTime geocodedAt;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Ghi chú của Admin khi yêu cầu bổ sung hồ sơ (UC-013). */
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    /** UC-008 (V51): denorm điểm review VISIBLE — cho phép sort marketplace theo rating. */
    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private java.math.BigDecimal avgRating = java.math.BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private int ratingCount = 0;

    /**
     * Optimistic lock (P1 batch 2): chống hai admin cùng xử lý một hồ sơ (approve
     * vs reject) ghi đè quyết định của nhau. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
