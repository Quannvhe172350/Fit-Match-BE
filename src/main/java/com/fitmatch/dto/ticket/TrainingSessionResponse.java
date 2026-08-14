package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.TrainingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSessionResponse {

    private Long id;
    private Long ticketId;
    private Integer dayIndex;
    private LocalDate sessionDate;
    private SessionStatus status;

    private Long ptProfileId;
    private String ptName;
    private LocalTime ptSlotStart;
    private LocalTime ptSlotEnd;

    /** Chỉ có nghĩa với buổi có PT — buổi tự tập không có check-in. */
    private LocalDateTime checkedInAt;

    private LocalDateTime ptConfirmedAt;
    private String evidenceUrl;

    public static TrainingSessionResponse of(TrainingSession s) {
        return TrainingSessionResponse.builder()
                .id(s.getId())
                .ticketId(s.getTicket().getId())
                .dayIndex(s.getDayIndex())
                .sessionDate(s.getSessionDate())
                .status(s.getStatus())
                .ptProfileId(s.getPtProfile() != null ? s.getPtProfile().getId() : null)
                .ptName(s.getPtProfile() != null ? s.getPtProfile().getDisplayName() : null)
                .ptSlotStart(s.getPtSlotStart())
                .ptSlotEnd(s.getPtSlotEnd())
                .checkedInAt(s.getCheckedInAt())
                .ptConfirmedAt(s.getPtConfirmedAt())
                .evidenceUrl(s.getEvidenceUrl())
                .build();
    }
}
