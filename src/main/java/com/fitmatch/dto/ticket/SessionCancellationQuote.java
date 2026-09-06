package com.fitmatch.dto.ticket;

import com.fitmatch.service.support.SessionCancelRefundCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * V94: khách sẽ nhận lại bao nhiêu nếu huỷ buổi này NGAY BÂY GIỜ.
 *
 * <p>Có endpoint riêng để hỏi trước vì huỷ là thao tác không lùi được và số tiền
 * phụ thuộc vào lúc bấm: bắt khách bấm "Huỷ" rồi mới biết mình mất bao nhiêu là
 * đặt câu hỏi sau khi đã trả lời. FE hiện con số này ngay trong hộp xác nhận.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCancellationQuote {

    private Long sessionId;

    /** Huỷ được hay không — false thì {@link #reason} nói vì sao. */
    private boolean cancellable;

    /** Lý do không huỷ được; null khi {@link #cancellable} = true. */
    private String reason;

    /** Số giờ còn lại tới đầu buổi. Âm = buổi đã bắt đầu. */
    private long hoursAhead;

    /** Tỉ lệ hoàn áp dụng ở thời điểm hỏi, 0..100. */
    private BigDecimal refundPercent;

    /** Số tiền thực nhận về ví nếu huỷ ngay. */
    private BigDecimal refundAmount;

    /** Giá trị một ngày tập của vé — để FE giải thích con số trên. */
    private BigDecimal perDayValue;

    /** Mốc "hoàn 100%" của gym, giờ. Hiện trong hộp xác nhận để khách hiểu luật. */
    private int fullRefundHours;

    /** Mốc hoàn một phần của gym, giờ. */
    private int partialRefundHours;

    /** Tỉ lệ của mốc một phần, 0..100. */
    private BigDecimal partialRefundPercent;

    public static SessionCancellationQuote of(Long sessionId,
                                              SessionCancelRefundCalculator.Quote quote,
                                              SessionCancelRefundCalculator.Tiers tiers,
                                              String reason) {
        return SessionCancellationQuote.builder()
                .sessionId(sessionId)
                .cancellable(reason == null)
                .reason(reason)
                .hoursAhead(quote.hoursAhead())
                .refundPercent(quote.percent())
                .refundAmount(quote.amount())
                .perDayValue(quote.perDayValue())
                .fullRefundHours(tiers.fullHours())
                .partialRefundHours(tiers.partialHours())
                .partialRefundPercent(tiers.partialPercent())
                .build();
    }
}
