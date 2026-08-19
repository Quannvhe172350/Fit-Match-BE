package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.PtCancellationStatus;
import com.fitmatch.entity.SessionPtCancellation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Quyết định §4.1 nhìn từ phía KHÁCH: buổi tập mất PT vì đơn nghỉ được duyệt,
 * và khách còn phải chọn — đổi PT khác, hay nhận hoàn phụ phí PT của ngày đó.
 *
 * <p>{@code estimatedRefund} tính tại thời điểm đọc chứ không lưu: khách phải
 * thấy con số trước khi bấm, và số đó phụ thuộc phần đã hoàn lẻ trước đó của
 * cùng một vé.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPtCancellationDto {

    private Long id;
    private Long sessionId;
    private LocalDate sessionDate;

    private Long formerPtProfileId;
    private String formerPtName;
    private LocalTime formerSlotStart;
    private LocalTime formerSlotEnd;

    private PtCancellationStatus status;

    /** Số tiền sẽ hoàn nếu khách chọn hoàn — null/0 nghĩa là chỉ còn đường đổi PT. */
    private BigDecimal estimatedRefund;

    /** Số tiền đã hoàn thật; chỉ có giá trị khi status = REFUNDED. */
    private BigDecimal refundAmount;

    private LocalDateTime resolvedAt;

    public static SessionPtCancellationDto of(SessionPtCancellation c, BigDecimal estimatedRefund) {
        return SessionPtCancellationDto.builder()
                .id(c.getId())
                .sessionId(c.getTrainingSession() != null ? c.getTrainingSession().getId() : null)
                .sessionDate(c.getTrainingSession() != null
                        ? c.getTrainingSession().getSessionDate() : null)
                .formerPtProfileId(c.getFormerPtProfile() != null
                        ? c.getFormerPtProfile().getId() : null)
                .formerPtName(c.getFormerPtProfile() != null
                        ? c.getFormerPtProfile().getDisplayName() : null)
                .formerSlotStart(c.getFormerSlotStart())
                .formerSlotEnd(c.getFormerSlotEnd())
                .status(c.getStatus())
                .estimatedRefund(estimatedRefund)
                .refundAmount(c.getRefundAmount())
                .resolvedAt(c.getResolvedAt())
                .build();
    }
}
