package com.fitmatch.entity;

import com.fitmatch.common.enums.CatalogStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dịch vụ kèm vé (V82) — add-on tính tiền một lần mà khách tick thêm lúc mua:
 * xông hơi, tủ đồ riêng, khăn, nước...
 *
 * <p>KHÔNG phải bảng {@code gym_services} cũ đã bị V81 drop: dịch vụ ở đây
 * không đặt lịch được và không phải đơn vị nghiệp vụ. Vé vẫn là thứ duy nhất
 * sinh buổi tập, thanh toán và settlement — dịch vụ chỉ cộng tiền vào vé.
 *
 * <p>Khai ở cấp gym, áp dụng cho mọi chi nhánh. Xem V82__gym_services.sql.
 */
@Entity
@Table(name = "gym_services", indexes =
        @Index(name = "idx_gym_services_gym", columnList = "gym_profile_id,status"))
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

    /** Giá cộng thêm một lần cho cả vé, KHÔNG nhân theo số ngày. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Vòng đời hiển thị, dùng chung enum với catalog vé. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CatalogStatus status = CatalogStatus.PUBLISHED;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
