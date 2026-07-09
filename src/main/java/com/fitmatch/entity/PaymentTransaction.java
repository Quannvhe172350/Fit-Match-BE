package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * Giao dịch ngân hàng do Casso gửi qua webhook (UC-053). {@code externalId} là
 * id giao dịch Casso — UNIQUE để bảo đảm idempotency (webhook có thể bắn trùng).
 * Schema: payment_transactions(id, payment_order_id FK?, external_id UNIQUE,
 * amount, ref_code, raw_description, + audit).
 */
@Entity
@Table(name = "payment_transactions", indexes =
        @Index(name = "idx_paytxn_external", columnList = "external_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Đơn thanh toán khớp được (null nếu không khớp refCode nào). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private PaymentOrder paymentOrder;

    /** Id giao dịch từ Casso — khoá idempotency. */
    @Column(name = "external_id", nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "ref_code", length = 100)
    private String refCode;

    @Column(name = "raw_description", length = 500)
    private String rawDescription;
}
