package com.fitmatch.dto.pt;

import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.LeaveType;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Đơn nghỉ nhìn từ cả hai phía — PT xem đơn của mình, Gym xem hàng chờ duyệt. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtLeaveRequestResponse {

    private Long id;
    private Long ptProfileId;
    private String ptName;

    private LeaveType type;
    private LeaveScope scope;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<Long> shiftIds;
    private List<String> shiftNames;

    private String reason;
    private String attachmentUrl;
    private LeaveStatus status;

    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;

    public static PtLeaveRequestResponse of(PtLeaveRequest r) {
        return PtLeaveRequestResponse.builder()
                .id(r.getId())
                .ptProfileId(r.getPtProfile() != null ? r.getPtProfile().getId() : null)
                .ptName(r.getPtProfile() != null ? r.getPtProfile().getDisplayName() : null)
                .type(r.getType())
                .scope(r.getScope())
                .fromDate(r.getFromDate())
                .toDate(r.getToDate())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .shiftIds(r.getShifts().stream().map(GymShift::getId).toList())
                .shiftNames(r.getShifts().stream().map(GymShift::getName).toList())
                .reason(r.getReason())
                .attachmentUrl(r.getAttachmentUrl())
                .status(r.getStatus())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt())
                .rejectReason(r.getRejectReason())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
