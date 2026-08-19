package com.fitmatch.entity;

import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.LeaveType;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Đơn xin nghỉ / báo bận của PT (V87). Trong mô hình mới PT không còn khai lịch
 * rảnh — đây là cách duy nhất PT tác động lên lịch của chính mình.
 *
 * <p>Đơn PHẢI được Gym duyệt mới có hiệu lực (quyết định §4.2). Cho PT tự khoá
 * slot thì ràng buộc bảo vệ buổi khách đã đặt (§4.1) mất nghĩa hoàn toàn.
 *
 * <p>Phạm vi đọc theo hai chiều: {@link #fromDate}..{@link #toDate} là chiều
 * NGÀY, còn {@link #scope} quyết chiều GIỜ áp cho mọi ngày trong khoảng đó.
 */
@Entity
@Table(name = "pt_leave_requests", indexes = {
        @Index(name = "idx_pt_leave_pt_range",
                columnList = "pt_profile_id,status,from_date,to_date"),
        @Index(name = "idx_pt_leave_gym_status",
                columnList = "gym_profile_id,status,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtLeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    /**
     * Bản sao Gym chủ quản của PT. Màn duyệt đơn lọc theo (gym, status) liên
     * tục — không được join qua {@code pt_profiles} cho mỗi lần mở danh sách.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveScope scope;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** Chỉ dùng khi {@code scope = TIME_RANGE}. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** Chỉ dùng khi {@code scope = SHIFT} — PT nghỉ ca sáng nhưng vẫn dạy ca chiều. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "pt_leave_request_shifts",
            joinColumns = @JoinColumn(name = "leave_request_id"),
            inverseJoinColumns = @JoinColumn(name = "gym_shift_id"))
    @Builder.Default
    private Set<GymShift> shifts = new LinkedHashSet<>();

    @Column(nullable = false, length = 1000)
    private String reason;

    /** URL từ /api/files/upload — giấy nghỉ ốm, đơn thuốc... Tuỳ chọn. */
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    /** Đơn còn sống — đang chờ duyệt hoặc đã duyệt. Dùng cho kiểm chồng đơn và hạn mức. */
    public boolean isEffective() {
        return status == LeaveStatus.PENDING || status == LeaveStatus.APPROVED;
    }
}
