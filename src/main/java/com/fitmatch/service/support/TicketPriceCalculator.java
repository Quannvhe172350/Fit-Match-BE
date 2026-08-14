package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Ticket;
import com.fitmatch.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Chốt giá vé tại thời điểm mua — thay {@code BookingPriceCalculator}.
 *
 * <pre>
 *   total    = price + (withPt ? ptSurchargePerDay * dayCount : 0)   (câu 6)
 *              + servicesAmount                                      (V82)
 *   sau đó trừ voucher, rồi trừ điểm thưởng
 *   payable  = phần còn lại, làm tròn HALF_UP về số nguyên VND
 * </pre>
 *
 * <p>Dịch vụ cộng MỘT LẦN cho cả vé, khác phụ phí PT vốn nhân theo số ngày.
 *
 * <p>Không còn khái niệm đặt cọc (câu 25) — khách trả đủ ngay khi mua.
 *
 * <p>P1-26 giữ nguyên: payable PHẢI là số nguyên VND, vì VietQR sinh số tiền
 * bằng {@code toBigInteger()}. Lệch một đồng là webhook luôn thấy "trả thiếu"
 * và vé không bao giờ được kích hoạt.
 */
@Component
public class TicketPriceCalculator {

    /** 1 điểm thưởng = 1.000đ giảm giá — khớp POINT_VALUE_VND của LoyaltyServiceImpl. */
    public static final BigDecimal POINT_VALUE_VND = BigDecimal.valueOf(1_000);

    /**
     * Kết quả tính giá, dùng chung cho cả /tickets/quote (hiển thị trước khi mua)
     * và /tickets/purchase (ghi vào vé) để hai đường không bao giờ lệch nhau.
     */
    public record TicketPricing(
            BigDecimal totalAmount,
            BigDecimal servicesAmount,
            BigDecimal voucherDiscount,
            int loyaltyPointsUsed,
            BigDecimal loyaltyDiscount,
            BigDecimal payableAmount) {
    }

    /**
     * @param price             giá vé không kèm PT (snapshot từ TicketType)
     * @param ptSurchargePerDay phụ phí PT mỗi ngày; bỏ qua khi {@code withPt} = false
     * @param dayCount          số ngày của vé (DAY = 1)
     * @param withPt            khách có chọn PT hay không
     * @param voucherDiscount   số tiền voucher giảm, null = không dùng voucher
     * @param availablePoints   số điểm khả dụng của khách; {@code useLoyaltyPoints}
     *                          = false thì truyền 0
     */
    public TicketPricing calculate(BigDecimal price, BigDecimal ptSurchargePerDay, int dayCount,
                                   boolean withPt, BigDecimal voucherDiscount, int availablePoints) {
        return calculate(price, ptSurchargePerDay, dayCount, withPt, BigDecimal.ZERO,
                voucherDiscount, availablePoints);
    }

    /**
     * @param servicesAmount V82: tổng tiền dịch vụ kèm vé, cộng MỘT LẦN (không
     *                       nhân theo ngày như phụ phí PT) và cộng TRƯỚC voucher
     *                       để voucher giảm trên tổng khách thực trả.
     */
    public TicketPricing calculate(BigDecimal price, BigDecimal ptSurchargePerDay, int dayCount,
                                   boolean withPt, BigDecimal servicesAmount,
                                   BigDecimal voucherDiscount, int availablePoints) {
        if (dayCount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "dayCount must be positive");
        }
        BigDecimal services = servicesAmount != null ? servicesAmount : BigDecimal.ZERO;
        if (services.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "servicesAmount must be >= 0");
        }
        BigDecimal total = price;
        if (withPt && ptSurchargePerDay != null && ptSurchargePerDay.compareTo(BigDecimal.ZERO) > 0) {
            total = total.add(ptSurchargePerDay.multiply(BigDecimal.valueOf(dayCount)));
        }
        total = total.add(services);

        // Voucher trước, điểm thưởng sau: điểm chỉ phủ phần còn thiếu nên khách
        // không "đốt" điểm vào phần đã được voucher giảm.
        BigDecimal voucher = voucherDiscount != null ? voucherDiscount : BigDecimal.ZERO;
        voucher = voucher.max(BigDecimal.ZERO).min(total);
        BigDecimal remaining = total.subtract(voucher);

        int pointsUsed = 0;
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;
        if (availablePoints > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Câu 14: toggle là tiêu TOÀN BỘ điểm khả dụng, cap ở số còn phải trả.
            // Điểm cuối cùng được phép chỉ dùng một phần giá trị (làm tròn lên số
            // điểm, cắt giá trị giảm ở remaining) để khách bấm toggle là về 0đ,
            // thay vì còn dư vài trăm đồng lẻ không thanh toán nổi.
            BigDecimal maxFromPoints = POINT_VALUE_VND.multiply(BigDecimal.valueOf(availablePoints));
            loyaltyDiscount = maxFromPoints.min(remaining);
            pointsUsed = loyaltyDiscount.divide(POINT_VALUE_VND, 0, RoundingMode.CEILING).intValue();
            remaining = remaining.subtract(loyaltyDiscount);
        }

        BigDecimal payable = remaining.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
        return new TicketPricing(total, services, voucher, pointsUsed, loyaltyDiscount, payable);
    }

    /** Ghi kết quả tính giá vào vé (dùng ở /tickets/purchase). */
    public void applyTo(Ticket ticket, TicketPricing pricing) {
        ticket.setTotalAmount(pricing.totalAmount());
        ticket.setServicesAmount(pricing.servicesAmount());
        ticket.setDiscountAmount(pricing.voucherDiscount().add(pricing.loyaltyDiscount()));
        ticket.setLoyaltyPointsUsed(pricing.loyaltyPointsUsed() > 0 ? pricing.loyaltyPointsUsed() : null);
        ticket.setPayableAmount(pricing.payableAmount());
    }
}
