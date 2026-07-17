package com.fitmatch.entity;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.DisputeStatus;
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
import java.time.LocalDateTime;

/**
 * Tranh chấp/khiếu nại gắn với một booking (UC-063). Truy vết được tới
 * booking/payment/session. Người mở lấy từ BaseEntity.createdBy + openedByRole.
 * Schema: disputes(id, booking_id FK, opened_by_role, reason, status, resolution,
 * refund_amount, frozen_amount, moderator_note, resolved_at, + audit).
 */
@Entity
@Table(name = "disputes", indexes = {
        @Index(name = "idx_disputes_booking", columnList = "booking_id"),
        @Index(name = "idx_disputes_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Vai trò của người mở (CUSTOMER/GYM_OPERATOR/PT) — để hiển thị góc nhìn. */
    @Column(name = "opened_by_role", length = 30)
    private String openedByRole;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DisputeResolution resolution;

    /** Số tiền hoàn cho khách khi resolution có hoàn một phần (UC-067). */
    @Column(name = "refund_amount", precision = 14, scale = 2)
    private BigDecimal refundAmount;

    /** Số tiền đang giữ (held) tại thời điểm mở tranh chấp — cơ sở áp dụng tài chính. */
    @Column(name = "frozen_amount", precision = 14, scale = 2)
    private BigDecimal frozenAmount;

    @Column(name = "moderator_note", length = 1000)
    private String moderatorNote;

    /** D-12 (V39): moderator nhận xử lý case khi startReview — hai người không dẫm chân nhau. */
    @Column(name = "assigned_moderator", length = 50)
    private String assignedModerator;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Optimistic lock (UC-066/067): chống hai lệnh resolve song song cùng áp
     * tài chính trên frozenAmount. Xem V32__disputes_version.sql.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
