package com.fitmatch.entity;

import com.fitmatch.common.enums.IssueTargetType;
import com.fitmatch.common.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Báo cáo vấn đề dịch vụ/hành vi (UC-070) — nhắm tới Gym/PT/Booking, bổ sung
 * cho report review (ReviewReport). Người báo cáo = BaseEntity.createdBy.
 * targetId là ID mềm (không FK) vì trỏ tới nhiều bảng theo targetType;
 * service validate tồn tại khi tạo.
 * Schema: issue_reports(id, target_type, target_id, reason, status, moderator_note, + audit).
 */
@Entity
@Table(name = "issue_reports", indexes = {
        @Index(name = "idx_issue_reports_status", columnList = "status"),
        @Index(name = "idx_issue_reports_target", columnList = "target_type, target_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private IssueTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "moderator_note", length = 500)
    private String moderatorNote;
}
