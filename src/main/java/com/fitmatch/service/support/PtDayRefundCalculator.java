package com.fitmatch.service.support;

import com.fitmatch.entity.Ticket;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quyết định §4.1: PT xin nghỉ và khách chọn "hoàn tiền" thì hoàn ĐÚNG phần
 * phụ phí PT của MỘT ngày, không phải tiền vé của ngày đó.
 *
 * <p>Vì sao chỉ phụ phí PT: vé có giá trị cả ngày, khách vẫn vào tập bình
 * thường — thứ khách mất là huấn luyện viên, và {@code ptSurchargePerDay} đúng
 * là giá của thứ đó. Phụ phí này được tính cho MỌI ngày của vé
 * ({@code totalAmount = unitPrice + ptSurchargePerDay * dayCount + ...}) nên
 * hoàn một ngày không chồng lấn với ngày khác.
 *
 * <pre>
 *   gross     = ptSurchargePerDay                    (giá niêm yết, trước giảm)
 *   refund    = payableAmount * gross / totalAmount  (HALF_UP, scale 0 — VND)
 *   remaining = payableAmount - ptRefundedAmount
 *   final     = min(refund, remaining)
 * </pre>
 *
 * <p>Nhân {@code payableAmount / totalAmount} chứ không hoàn thẳng {@code gross}:
 * voucher và điểm thưởng đã giảm giá toàn vé, hoàn nguyên giá niêm yết sẽ trả
 * cho khách nhiều hơn số khách thực trả. Cap ở phần còn lại giữ bất biến
 * "tổng hoàn không bao giờ vượt payableAmount" — thứ mà
 * {@code WalletServiceImpl} không tự bảo đảm được vì nó kiểm held_balance TỔNG
 * của Gym chứ không theo từng vé.
 */
@Component
public class PtDayRefundCalculator {

    /**
     * Số tiền hoàn cho một ngày mất PT. Trả 0 khi vé không kèm PT, khi
     * voucher/điểm đã phủ hết vé ({@code payableAmount = 0}), hoặc khi vé đã
     * hoàn hết phần có thể hoàn.
     */
    public BigDecimal refundForOneDay(Ticket ticket) {
        if (!ticket.isWithPt()) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = ticket.getPtSurchargePerDay();
        BigDecimal total = ticket.getTotalAmount();
        BigDecimal payable = ticket.getPayableAmount();
        if (gross == null || total == null || payable == null
                || gross.signum() <= 0 || total.signum() <= 0 || payable.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal refund = payable.multiply(gross).divide(total, 0, RoundingMode.HALF_UP);
        BigDecimal alreadyRefunded = ticket.getPtRefundedAmount() == null
                ? BigDecimal.ZERO : ticket.getPtRefundedAmount();
        BigDecimal remaining = payable.subtract(alreadyRefunded).max(BigDecimal.ZERO);
        return refund.min(remaining);
    }
}
