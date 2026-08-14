package com.fitmatch.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Màn hình admin quyết mức hoàn (câu 11): đặt hai lựa chọn cạnh nhau kèm số
 * tiền thật, để người duyệt không phải tự nhẩm.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRefundPreviewResponse {

    private Long refundRequestId;
    private Long ticketId;
    private String ticketTypeName;
    private String customerName;

    private BigDecimal paidAmount;
    private Integer dayCount;
    private LocalDate startDate;
    private Integer elapsedDays;

    /** Lựa chọn 1 — hoàn toàn bộ. */
    private BigDecimal fullRefund;

    /** Lựa chọn 2 — trừ số ngày đã qua. */
    private BigDecimal partialRefund;
    private BigDecimal retained;

    /**
     * Câu 13: vé chưa dùng ngày nào thì hai lựa chọn cho ra cùng một số, FE chỉ
     * hiện một nút "hoàn 100%" thay vì bắt admin chọn giữa hai thứ giống hệt nhau.
     */
    private boolean fullRefundOnly;

    /** Số buổi tương lai sẽ bị huỷ nếu duyệt — dùng cho cảnh báo (câu 12). */
    private int futureSessionsToCancel;
}
