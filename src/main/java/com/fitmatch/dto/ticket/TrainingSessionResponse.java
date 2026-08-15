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

    /**
     * Chỉ điền ở lịch dạy của PT ({@link #forPt}). Buổi tập vốn chỉ mang
     * {@code ticketId}, mà PT nhìn "vé #123" thì không biết mình dạy ai; còn ở
     * lịch của chính khách thì tên đó là tên họ, thêm vào chỉ tốn chỗ.
     */
    private String customerName;

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

    /** Bản cho lịch dạy của PT — kèm tên khách của vé. */
    public static TrainingSessionResponse forPt(TrainingSession s) {
        TrainingSessionResponse response = of(s);
        var customer = s.getTicket() != null ? s.getTicket().getCustomer() : null;
        if (customer != null) {
            response.setCustomerName(customer.getFullName());
        }
        return response;
    }
}
