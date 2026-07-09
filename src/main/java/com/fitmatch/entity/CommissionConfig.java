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

import java.math.BigDecimal;

/**
 * Cấu hình kinh tế nền tảng do Admin quản lý (UC-072): hoa hồng, phí nền tảng và
 * số ngày giữ tiền trước khi giải ngân về Gym. Dùng mẫu "một dòng hiệu lực" —
 * luôn đọc bản mới nhất; mỗi lần đổi tạo bản ghi mới để giữ lịch sử.
 * Schema: commission_configs(id, commission_percent, platform_fee_percent,
 * settlement_hold_days, + audit).
 */
@Entity
@Table(name = "commission_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** % hoa hồng nền tảng thu trên mỗi settlement về Gym (0-100). */
    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /** % phí nền tảng bổ sung tính trên tổng booking (0-100). */
    @Column(name = "platform_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercent;

    /** Số ngày giữ tiền ở pending settlement (cửa sổ khiếu nại) trước khi release. */
    @Column(name = "settlement_hold_days", nullable = false)
    private Integer settlementHoldDays;
}
