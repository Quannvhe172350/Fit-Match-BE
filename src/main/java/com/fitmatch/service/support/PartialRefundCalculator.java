package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Ticket;
import com.fitmatch.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Câu 11: Admin chọn hoàn toàn bộ hay trừ số ngày đã qua. Lớp này tính hai con
 * số của phương án PARTIAL_ELAPSED và bảo đảm KHÔNG lệch một đồng nào:
 *
 * <pre>
 *   perDay   = payableAmount / dayCount            (HALF_UP, scale 0 — VND)
 *   elapsed  = clamp(today - startDate + 1, 0, dayCount)
 *   retained = min(perDay * elapsed, payableAmount)
 *   refund   = payableAmount - retained
 * </pre>
 *
 * <p>Điểm mấu chốt: {@code refund} luôn là phần BÙ TRỪ chứ không tính độc lập,
 * nên phần dư của phép làm tròn luôn thuộc về khoản hoàn cho khách và bất biến
 * {@code refund + retained == payableAmount} đúng với mọi bộ số.
 *
 * <p>V89: {@code payableAmount} ở mọi công thức trên được hiểu là phần CÒN LẠI
 * sau khi trừ {@code ptRefundedAmount} — số đã hoàn lẻ cho những ngày PT xin
 * nghỉ (quyết định §4.1). Không trừ thì hoàn lẻ 200k rồi hoàn cả vé 2tr sẽ
 * chuyển cho khách 2,2tr trên một vé thu 2tr, và WalletService KHÔNG bắt được
 * vì nó chỉ kiểm held_balance tổng của Gym chứ không theo từng vé.
 */
@Component
public class PartialRefundCalculator {

    /**
     * @param elapsedDays số ngày đã tính là đã sử dụng (tính cả ngày bắt đầu)
     * @param retained    phần giữ lại chuyển cho gym
     * @param refund      phần hoàn về ví khách
     */
    public record RefundSplit(int elapsedDays, BigDecimal retained, BigDecimal refund) {
    }

    /** Phương án hoàn toàn bộ — giữ lại 0đ. */
    public RefundSplit full(Ticket ticket) {
        BigDecimal payable = requirePayable(ticket);
        return new RefundSplit(elapsedDays(ticket, LocalDate.now()), BigDecimal.ZERO, payable);
    }

    /** Phương án trừ số ngày đã qua. */
    public RefundSplit partialElapsed(Ticket ticket) {
        return partialElapsed(ticket, LocalDate.now());
    }

    /** Bản nhận {@code today} tường minh để test không phụ thuộc đồng hồ máy. */
    public RefundSplit partialElapsed(Ticket ticket, LocalDate today) {
        BigDecimal payable = requirePayable(ticket);
        int dayCount = ticket.getDayCount();
        if (dayCount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ticket dayCount must be positive");
        }
        int elapsed = elapsedDays(ticket, today);

        BigDecimal perDay = payable.divide(BigDecimal.valueOf(dayCount), 0, RoundingMode.HALF_UP);
        BigDecimal retained = perDay.multiply(BigDecimal.valueOf(elapsed)).min(payable);
        BigDecimal refund = payable.subtract(retained);
        return new RefundSplit(elapsed, retained, refund);
    }

    /**
     * Số ngày đã dùng, tính CẢ ngày bắt đầu (mua rồi tập ngay hôm nay = 1 ngày).
     * Vé chưa đặt lịch (startDate null) hoặc chưa tới ngày bắt đầu = 0 ngày, tức
     * hoàn 100% — khớp câu 13: vé chưa dùng buổi nào thì admin chỉ có một lựa chọn.
     */
    public int elapsedDays(Ticket ticket, LocalDate today) {
        LocalDate start = ticket.getStartDate();
        if (start == null || today.isBefore(start)) {
            return 0;
        }
        long elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1;
        return (int) Math.min(elapsed, ticket.getDayCount());
    }

    /**
     * Phần tiền còn hoàn được của vé: số khách đã trả trừ đi phần phụ phí PT đã
     * hoàn lẻ theo từng buổi. Mọi công thức trong lớp này đi qua đây, nên bất
     * biến "tổng hoàn không vượt payableAmount" đúng cho cả hai đường hoàn.
     */
    private BigDecimal requirePayable(Ticket ticket) {
        BigDecimal payable = ticket.getPayableAmount();
        if (payable == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Ticket has no payable amount");
        }
        BigDecimal ptRefunded = ticket.getPtRefundedAmount();
        if (ptRefunded == null || ptRefunded.signum() <= 0) {
            return payable;
        }
        return payable.subtract(ptRefunded).max(BigDecimal.ZERO);
    }
}
