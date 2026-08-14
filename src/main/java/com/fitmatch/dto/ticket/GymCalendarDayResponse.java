package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.entity.TrainingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lịch quản lý của gym, ĐÃ GOM THEO NGÀY ở server. Đây là điểm sửa của bẫy cũ:
 * FE không còn tải 200 bản ghi rồi tự gom ở client, mà hỏi đúng khoảng ngày
 * đang xem.
 *
 * <p>Read-only hoàn toàn — gym không duyệt, không từ chối, không đổi lịch
 * (quyết định #7). Thao tác duy nhất là xác nhận buổi có PT kèm ảnh.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymCalendarDayResponse {

    private LocalDate date;
    private List<Entry> sessions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private Long sessionId;
        private Long ticketId;
        private String customerName;
        private TicketKind ticketKind;
        private String ticketName;
        /** Vé gói hiện "ngày 3/10"; vé ngày thì cả hai đều = 1. */
        private Integer dayIndex;
        private Integer dayCount;
        private String ptName;
        private LocalTime slotStart;
        private LocalTime slotEnd;
        private boolean checkedIn;
        private boolean ptConfirmed;

        public static Entry of(TrainingSession s) {
            return Entry.builder()
                    .sessionId(s.getId())
                    .ticketId(s.getTicket().getId())
                    .customerName(s.getTicket().getCustomer().getFullName())
                    .ticketKind(s.getTicket().getKind())
                    .ticketName(s.getTicket().getTicketType().getName())
                    .dayIndex(s.getDayIndex())
                    .dayCount(s.getTicket().getDayCount())
                    .ptName(s.getPtProfile() != null ? s.getPtProfile().getDisplayName() : null)
                    .slotStart(s.getPtSlotStart())
                    .slotEnd(s.getPtSlotEnd())
                    .checkedIn(s.getCheckedInAt() != null)
                    .ptConfirmed(s.getPtConfirmedAt() != null)
                    .build();
        }
    }
}
