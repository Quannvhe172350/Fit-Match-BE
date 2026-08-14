package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.TicketStatusHistory;
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
public class TicketStatusHistoryResponse {

    private Long id;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private String reason;
    private String actor;
    private LocalDateTime at;

    public static TicketStatusHistoryResponse of(TicketStatusHistory h) {
        return TicketStatusHistoryResponse.builder()
                .id(h.getId())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .reason(h.getReason())
                .actor(h.getCreatedBy())
                .at(h.getCreatedAt())
                .build();
    }
}
