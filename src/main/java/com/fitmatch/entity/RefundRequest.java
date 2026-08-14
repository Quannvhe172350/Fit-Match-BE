package com.fitmatch.entity;

import com.fitmatch.common.enums.RefundMode;
import com.fitmatch.common.enums.RefundStatus;
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

/**
 * Yêu cầu hoàn tiền / điều chỉnh gắn với một vé (UC-055/056).
 * Schema: refund_requests(id, ticket_id FK, amount, reason, status,
 * decision_note, + audit).
 */
@Entity
@Table(name = "refund_requests", indexes =
        @Index(name = "idx_refund_ticket", columnList = "ticket_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Yêu cầu hoàn của một vé. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    /** Số tiền khách YÊU CẦU hoàn (toàn bộ phần đang giữ). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Câu 11: cách admin quyết mức hoàn. Null khi chưa duyệt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", length = 20)
    private RefundMode refundMode;

    /** Số ngày đã dùng tại thời điểm duyệt — lưu lại để đối soát về sau. */
    @Column(name = "elapsed_days")
    private Integer elapsedDays;

    /** Phần giữ lại cho gym. Bất biến: retained + số thực hoàn = amount. */
    @Column(name = "retained_amount", precision = 14, scale = 2)
    private BigDecimal retainedAmount;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /**
     * Optimistic lock (P1 batch 2): chống hai admin cùng approve/execute một
     * yêu cầu hoàn -> double refund trên cùng khoản held. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
