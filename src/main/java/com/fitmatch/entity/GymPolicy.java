package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Chính sách vận hành của Gym (UC-017): đặt lịch, hủy, no-show, nội quy.
 * Quan hệ 1-1 với {@link GymProfile}.
 * Schema: gym_policies(id, gym_profile_id FK UNIQUE, booking_policy,
 * cancellation_policy, no_show_policy, house_rules, + audit).
 */
@Entity
@Table(name = "gym_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false, unique = true)
    private GymProfile gymProfile;

    @Column(name = "booking_policy", length = 2000)
    private String bookingPolicy;

    @Column(name = "cancellation_policy", length = 2000)
    private String cancellationPolicy;

    @Column(name = "no_show_policy", length = 2000)
    private String noShowPolicy;

    @Column(name = "house_rules", length = 2000)
    private String houseRules;

    /**
     * V94 — luật huỷ MỘT NGÀY TẬP, dạng máy tính được. {@link #cancellationPolicy}
     * ở trên vẫn là văn bản cho người đọc; ba cột này mới là thứ quyết định số
     * tiền. Báo trước sớm hơn mốc đầu = hoàn 100%, sớm hơn mốc sau = hoàn
     * {@link #cancelPartialRefundPercent}%, muộn hơn nữa = không hoàn.
     */
    @Column(name = "cancel_full_refund_hours", nullable = false)
    @Builder.Default
    private Integer cancelFullRefundHours = 24;

    @Column(name = "cancel_partial_refund_hours", nullable = false)
    @Builder.Default
    private Integer cancelPartialRefundHours = 12;

    @Column(name = "cancel_partial_refund_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal cancelPartialRefundPercent = new java.math.BigDecimal("50.00");
}
