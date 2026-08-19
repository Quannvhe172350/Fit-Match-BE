package com.fitmatch.entity;

import com.fitmatch.common.enums.PtCancellationStatus;
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
import java.time.LocalTime;

/**
 * Một buổi tập vừa bị mất PT vì đơn nghỉ được duyệt, và KHÁCH còn một quyết
 * định treo: chọn PT thay thế, hay nhận hoàn phụ phí PT của đúng ngày đó
 * (quyết định §4.1).
 *
 * <p>Đây là trạng thái của QUYẾT ĐỊNH, không phải của buổi tập. Buổi vẫn
 * SCHEDULED suốt quá trình: vé có giá trị CẢ NGÀY nên khách mất PT chứ không
 * mất quyền vào tập. Nhờ vậy {@code SessionStatus} giữ nguyên ba giá trị —
 * thêm giá trị thứ tư sẽ lan vào {@code SessionLifecycle},
 * {@code session_status_history}, mọi bộ lọc FE và cả {@code markUsedUpIfComplete}.
 */
@Entity
@Table(name = "session_pt_cancellations", indexes = {
        @Index(name = "idx_session_ptcancel_session", columnList = "training_session_id,status"),
        @Index(name = "idx_session_ptcancel_pending", columnList = "status,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionPtCancellation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false)
    private TrainingSession trainingSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private PtLeaveRequest leaveRequest;

    /**
     * Snapshot PT đã bị gỡ. Buổi tập đã xoá {@code pt_profile_id} nên không
     * chép lại thì màn hình của khách không nói được "PT nào nghỉ".
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "former_pt_profile_id", nullable = false)
    private PtProfile formerPtProfile;

    @Column(name = "former_slot_start", nullable = false)
    private LocalTime formerSlotStart;

    @Column(name = "former_slot_end")
    private LocalTime formerSlotEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PtCancellationStatus status = PtCancellationStatus.PENDING_CUSTOMER;

    /** Số tiền đã hoàn về ví khách; null khi khách chọn PT thay thế. */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
