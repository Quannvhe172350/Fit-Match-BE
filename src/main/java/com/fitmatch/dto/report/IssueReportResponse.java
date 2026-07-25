package com.fitmatch.dto.report;

import com.fitmatch.common.enums.IssueTargetType;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.entity.IssueReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueReportResponse {

    private Long id;
    private IssueTargetType targetType;
    private Long targetId;
    /** Tên đối tượng để hiển thị (gym name / PT display name / mã booking). */
    private String targetName;
    private String reason;
    private ReportStatus status;
    private String moderatorNote;
    private String reportedBy;
    private LocalDateTime createdAt;

    public static IssueReportResponse of(IssueReport r, String targetName) {
        return IssueReportResponse.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetName(targetName)
                .reason(r.getReason())
                .status(r.getStatus())
                .moderatorNote(r.getModeratorNote())
                .reportedBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
