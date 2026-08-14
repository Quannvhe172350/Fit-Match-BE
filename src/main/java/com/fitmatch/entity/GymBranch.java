package com.fitmatch.entity;

import jakarta.persistence.Column;
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

    /**
     * Cấp hành chính dưới tỉnh/thành — đồng bộ với bộ lọc vị trí marketplace (UC-18).
     *
     * <p>V66: nay chứa PHƯỜNG/XÃ. Việt Nam bỏ cấp huyện từ đợt sắp xếp đơn vị hành
     * chính 2025, nên form địa chỉ điền phường vào đây. Tên cột giữ nguyên
     * {@code district} để không phải đổi cùng lúc cả bộ lọc marketplace, tìm kiếm
     * vé và màn so sánh gym — đổi tên là việc riêng, làm sau.
     *
     * <p>Bản ghi cũ vẫn đang giữ tên quận/huyện. Không backfill: cấp đó không còn
     * tồn tại nên không có bảng ánh xạ đáng tin nào để suy ra phường.
     */
    @Column(length = 100)
    private String district;

    /**
     * UC-18 (V55): toạ độ chi nhánh. Tìm kiếm theo bán kính khớp gym khi trụ sở
     * HOẶC bất kỳ chi nhánh đang hoạt động nào nằm trong bán kính — chuỗi gym
     * nhiều cơ sở nếu chỉ dựa vào trụ sở sẽ bị bỏ sót.
     */
    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(name = "place_id", length = 255)
    private String placeId;

    /** V65 — dịch vụ đã cấp {@link #placeId}; xem javadoc ở {@code GymProfile}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_provider", length = 20)
    private com.fitmatch.common.enums.GeocodingProvider placeProvider;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    /** V59: độ chính xác Google báo về (ROOFTOP / GEOMETRIC_CENTER / APPROXIMATE...). */
    @Column(name = "location_type", length = 30)
    private String locationType;

    /** V60: toạ độ do chủ gym tự kéo ghim — job làm mới định kỳ không được đụng vào. */
    @Column(name = "coordinates_pinned", nullable = false)
    @Builder.Default
    private boolean coordinatesPinned = false;

    @Column(name = "geocoded_at")
    private java.time.LocalDateTime geocodedAt;

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
