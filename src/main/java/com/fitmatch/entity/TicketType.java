package com.fitmatch.entity;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Loại vé do Gym bán — catalog duy nhất của hệ thống, thay hoàn toàn
 * {@code gym_services} + {@code training_packages}.
 *
 * <p>Vé khai báo ở cấp gym rồi tick chọn chi nhánh áp dụng qua
 * {@link TicketTypeBranch}; khách luôn mua kèm một chi nhánh cụ thể.
 * Xem V68__ticket_types.sql.
 */
@Entity
@Table(name = "ticket_types", indexes =
        @Index(name = "idx_ticket_types_gym", columnList = "gym_profile_id,status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    /** Các chi nhánh được bán loại vé này (câu 20). Rỗng = chưa bán ở đâu cả. */
    @OneToMany(mappedBy = "ticketType", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<TicketTypeBranch> branches = new ArrayList<>();

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketKind kind;

    /** Số ngày tập của vé. Vé DAY luôn = 1; vé PACKAGE = n ngày liên tiếp. */
    @Column(name = "day_count", nullable = false)
    private Integer dayCount;

    /** Giá vé KHÔNG kèm PT — đây là giá niêm yết trên marketplace. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Câu 6: phụ phí cho MỖI NGÀY khi khách chọn có PT. Giá vé có PT =
     * {@code price + ptSurchargePerDay * dayCount}. Null/0 = gym không tính thêm.
     */
    @Column(name = "pt_surcharge_per_day", precision = 12, scale = 2)
    private BigDecimal ptSurchargePerDay;

    /** Vòng đời hiển thị trên marketplace. {@code active} đồng bộ = PUBLISHED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CatalogStatus status = CatalogStatus.PUBLISHED;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Chống lost-update khi gym sửa giá đồng thời ở hai tab. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
