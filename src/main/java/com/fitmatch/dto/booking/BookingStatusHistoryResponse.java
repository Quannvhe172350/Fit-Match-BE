package com.fitmatch.dto.booking;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.BookingStatusHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một dòng lịch sử trạng thái booking (UC-040/045).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusHistoryResponse {

    private BookingStatus fromStatus;
    private BookingStatus toStatus;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;

    public static BookingStatusHistoryResponse of(BookingStatusHistory h) {
        return BookingStatusHistoryResponse.builder()
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .reason(h.getReason())
                .changedBy(h.getCreatedBy())
                .changedAt(h.getCreatedAt())
                .build();
    }
}
