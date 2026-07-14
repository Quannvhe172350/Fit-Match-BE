package com.fitmatch.dto.booking;

import com.fitmatch.entity.SessionNote;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Ghi chú buổi tập (UC-048/051). */
@Getter
@Builder
public class SessionNoteResponse {

    private Long id;
    private Long bookingId;
    private String note;
    private String evidenceUrl;
    private String author;
    private LocalDateTime createdAt;

    public static SessionNoteResponse of(SessionNote n) {
        return SessionNoteResponse.builder()
                .id(n.getId())
                .bookingId(n.getBooking().getId())
                .note(n.getNote())
                .evidenceUrl(n.getEvidenceUrl())
                .author(n.getCreatedBy())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
