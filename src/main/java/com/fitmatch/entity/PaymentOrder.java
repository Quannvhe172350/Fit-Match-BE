package com.fitmatch.entity;

import com.fitmatch.common.enums.PaymentStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đơn thanh toán cho một booking (UC-052). Thanh toán qua VietQR: khách chuyển
 * khoản với nội dung = refCode; Casso đối soát khớp refCode + amount (UC-053).
 * Schema: payment_orders(id, booking_id FK UNIQUE, ref_code UNIQUE, amount,
 * status, qr_content, paid_at, expires_at, + audit).
 */
@Entity
@Table(name = "payment_orders", indexes =
        @Index(name = "idx_payment_ref", columnList = "ref_code", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    /** Mã đối soát duy nhất, đặt trong nội dung chuyển khoản VietQR. */
    @Column(name = "ref_code", nullable = false, unique = true, length = 40)
    private String refCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Chuỗi nội dung VietQR (bank BIN|account|amount|addInfo) để FE render QR. */
    @Column(name = "qr_content", length = 500)
    private String qrContent;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Optimistic lock (P1 batch 2): chống hai webhook khác external_id cùng khớp
     * một order xử lý song song -> hold hai lần. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
