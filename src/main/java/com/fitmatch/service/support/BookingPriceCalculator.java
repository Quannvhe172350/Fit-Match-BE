package com.fitmatch.service.support;

import com.fitmatch.entity.Booking;
import com.fitmatch.entity.BookingRules;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UC-034: chốt snapshot giá tại checkout — tổng giá trị (giá dịch vụ hoặc gói)
 * và số phải trả ngay (đặt cọc theo booking rules UC-026; null = trả đủ 100%).
 * Hoa hồng/phí nền tảng tính ở phase payment-settlement (UC-072).
 */
@Component
public class BookingPriceCalculator {

    /** Gán totalAmount + payableAmount vào booking. */
    public void applyPricing(Booking booking) {
        // UC-049: buổi tập thuộc gói ĐÃ MUA — tiền đã trả ở booking mua gói.
        if (booking.getCustomerPackage() != null) {
            booking.setTotalAmount(BigDecimal.ZERO);
            booking.setPayableAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal total;
        BookingRules rules;
        if (booking.getGymService() != null) {
            total = booking.getGymService().getPrice();
            rules = booking.getGymService().getBookingRules();
        } else {
            total = booking.getTrainingPackage().getPrice();
            rules = booking.getTrainingPackage().getBookingRules();
        }

        BigDecimal payable = total;
        if (rules != null && rules.getDepositPercent() != null) {
            payable = total.multiply(BigDecimal.valueOf(rules.getDepositPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        booking.setTotalAmount(total);
        booking.setPayableAmount(payable);
    }
}
