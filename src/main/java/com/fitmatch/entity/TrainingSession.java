package com.fitmatch.entity;

import com.fitmatch.common.enums.SessionStatus;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Một ngày tập thuộc một vé.
 *
 * <p>{@link #sessionDate} là {@link LocalDate} chứ không phải LocalDateTime:
 * vé có giá trị CẢ NGÀY, khách vào lúc nào cũng được. Giờ giấc
 * ({@link #ptSlotStart}/{@link #ptSlotEnd}) chỉ tồn tại khi có PT và chỉ ràng
 * buộc lịch của PT — không ràng buộc giờ mở cửa hay sức chứa của chi nhánh.
 *
 * <p>Buổi tiêu theo NGÀY, không theo điểm danh (câu 9): SessionCompletionJob
 * chuyển SCHEDULED -> DONE khi ngày đã trôi qua. {@link #checkedInAt} chỉ là
 * ghi nhận có mặt cho buổi có PT, không ảnh hưởng trạng thái hay dòng tiền.
 * Xem V70__training_sessions.sql.
 */
@Entity
@Table(name = "training_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_ticket_day",
                columnNames = {"ticket_id", "day_index"}),
        indexes = {
                @Index(name = "idx_sessions_ticket", columnList = "ticket_id,day_index"),
                @Index(name = "idx_sessions_branch_date", columnList = "gym_branch_id,session_date"),
                @Index(name = "idx_sessions_pt_date", columnList = "pt_profile_id,session_date"),
                @Index(name = "idx_sessions_status_date", columnList = "status,session_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /**
     * Bản sao chi nhánh của vé. Lịch quản lý của gym đọc theo (chi nhánh, khoảng
     * ngày) — không được join ngược lên tickets cho từng ô lịch.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;

    /** Thứ tự ngày trong vé, 1..dayCount. Vé DAY luôn = 1. */
    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** PT phụ trách ngày này; null = tập tự do (vé có PT vẫn được để trống ngày lẻ). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pt_profile_id")
    private PtProfile ptProfile;

    @Column(name = "pt_slot_start")
    private LocalTime ptSlotStart;

    @Column(name = "pt_slot_end")
    private LocalTime ptSlotEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    /** Khách check-in — CHỈ dùng cho buổi có PT. Không đổi status, không đụng tiền. */
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    /** Câu 31/33: gym xác nhận PT có đến. Không chặn tiền, chỉ để lại bằng chứng. */
    @Column(name = "pt_confirmed_at")
    private LocalDateTime ptConfirmedAt;

    @Column(name = "pt_confirmed_by", length = 255)
    private String ptConfirmedBy;

    /** Ảnh bằng chứng buổi tập (thay session_notes đã bỏ ở câu 19). */
    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    /**
     * V94: số tiền đã hoàn khi khách huỷ đúng buổi này. Null = chưa huỷ; 0 =
     * huỷ quá muộn nên không được hoàn đồng nào — hai chuyện khác nhau, nên
     * không dùng 0 để biểu diễn "chưa huỷ".
     */
    @Column(name = "cancel_refund_amount", precision = 12, scale = 2)
    private BigDecimal cancelRefundAmount;

    /** Chống lost-update khi khách dời lịch trùng lúc job hoàn tất buổi. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
