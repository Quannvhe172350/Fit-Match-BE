package com.fitmatch.entity;

import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giao dịch ngân hàng do Casso gửi qua webhook (UC-053). {@code externalId} là
 * id giao dịch Casso — UNIQUE để bảo đảm idempotency (webhook có thể bắn trùng).
 * Schema: payment_transactions(id, payment_order_id FK?, external_id UNIQUE,
 * amount, ref_code, raw_description, recon_status, anomaly, resolution_note,
 * resolved_by, resolved_at, + audit).
 * <p>
 * Bảng này trước đây chỉ ghi (write-only) — tiền vào không khớp booking chỉ để
 * lại một dòng log rồi mất dấu. Nhóm cột {@code recon_*} biến nó thành hàng đợi
 * đối soát cho Finance (UC-053/056), xem
 * {@link com.fitmatch.service.PaymentReconciliationService}.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_paytxn_external", columnList = "external_id", unique = true),
        @Index(name = "idx_paytxn_recon", columnList = "recon_status")})
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

    /** Trạng thái đối soát — NEEDS_REVIEW là hàng đợi việc của Finance. */
    @Enumerated(EnumType.STRING)
    @Column(name = "recon_status", nullable = false, length = 20)
    @Builder.Default
    private ReconStatus reconStatus = ReconStatus.NEEDS_REVIEW;

    /** Lý do bất thường; null khi giao dịch khớp trọn vẹn. */
    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly", length = 20)
    private PaymentTxnAnomaly anomaly;

    /** Ghi chú quyết định của Finance khi tất toán giao dịch bất thường. */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
