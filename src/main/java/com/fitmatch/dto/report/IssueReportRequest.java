package com.fitmatch.dto.report;

import com.fitmatch.common.enums.IssueTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** UC-070: mở báo cáo vấn đề dịch vụ/hành vi cho Gym/PT/Booking. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueReportRequest {

    @NotNull(message = "Target type is required")
    private IssueTargetType targetType;

    @NotNull(message = "Target id is required")
    private Long targetId;

    @NotBlank(message = "Reason is required")
    @Size(max = 1000)
    private String reason;
}
