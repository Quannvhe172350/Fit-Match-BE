package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Câu 32: hạn sử dụng vé do Admin cấu hình. Dùng mẫu "một dòng hiệu lực" giống
 * {@link CommissionConfig} — V67 seed sẵn đúng một dòng (30 / 90 ngày).
 *
 * <p>Giá trị ở đây chỉ dùng để CHỐT {@code expires_at} tại thời điểm mua vé;
 * vé đã bán giữ nguyên hạn cũ khi Admin đổi cấu hình.
 */
@Entity
@Table(name = "platform_ticket_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformTicketConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Số ngày kể từ lúc mua mà vé DAY còn dùng được. */
    @Column(name = "day_ticket_expiry_days", nullable = false)
    private Integer dayTicketExpiryDays;

    /** Số ngày kể từ lúc mua mà vé PACKAGE còn dùng được. */
    @Column(name = "package_ticket_expiry_days", nullable = false)
    private Integer packageTicketExpiryDays;
}
