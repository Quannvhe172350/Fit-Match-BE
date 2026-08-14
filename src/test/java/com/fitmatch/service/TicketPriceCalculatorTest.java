package com.fitmatch.service;

import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.support.TicketPriceCalculator;
import com.fitmatch.service.support.TicketPriceCalculator.TicketPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Công thức giá vé: price + ptSurchargePerDay * dayCount, rồi voucher, rồi điểm. */
class TicketPriceCalculatorTest {

    private final TicketPriceCalculator calculator = new TicketPriceCalculator();

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount);
    }

    @Test
    void dayTicket_withoutPt_paysListPrice() {
        TicketPricing p = calculator.calculate(vnd(150_000), vnd(200_000), 1, false, null, 0);

        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(150_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(150_000));
    }

    /** Câu 6: phụ phí PT nhân theo SỐ NGÀY của gói, không phải một lần cho cả gói. */
    @Test
    void packageTicket_withPt_addsSurchargePerDay() {
        TicketPricing p = calculator.calculate(vnd(1_000_000), vnd(200_000), 10, true, null, 0);

        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(3_000_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(3_000_000));
    }

    @Test
    void withPtButNoSurchargeConfigured_chargesListPrice() {
        TicketPricing p = calculator.calculate(vnd(500_000), null, 5, true, null, 0);

        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(500_000));
    }

    @Test
    void voucher_reducesPayable() {
        TicketPricing p = calculator.calculate(vnd(500_000), null, 1, false, vnd(50_000), 0);

        assertThat(p.voucherDiscount()).isEqualByComparingTo(vnd(50_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(450_000));
    }

    /** Voucher lớn hơn giá vé không được sinh số âm. */
    @Test
    void voucher_cappedAtTotal() {
        TicketPricing p = calculator.calculate(vnd(100_000), null, 1, false, vnd(999_999), 0);

        assertThat(p.voucherDiscount()).isEqualByComparingTo(vnd(100_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Điểm chỉ ăn vào phần CÒN LẠI sau voucher, không ăn vào phần đã được giảm. */
    @Test
    void loyaltyPoints_appliedAfterVoucher() {
        TicketPricing p = calculator.calculate(vnd(500_000), null, 1, false, vnd(100_000), 50);

        assertThat(p.voucherDiscount()).isEqualByComparingTo(vnd(100_000));
        assertThat(p.loyaltyDiscount()).isEqualByComparingTo(vnd(50_000));
        assertThat(p.loyaltyPointsUsed()).isEqualTo(50);
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(350_000));
    }

    /** Câu 14: điểm phủ hết thì payable = 0 và vé ACTIVE ngay, không hiện QR. */
    @Test
    void loyaltyPoints_coverEverything_payableIsZero() {
        TicketPricing p = calculator.calculate(vnd(150_000), null, 1, false, null, 500);

        assertThat(p.loyaltyDiscount()).isEqualByComparingTo(vnd(150_000));
        assertThat(p.loyaltyPointsUsed()).isEqualTo(150);
        assertThat(p.payableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Không dùng điểm (toggle tắt) -> availablePoints = 0 -> không trừ gì. */
    @Test
    void loyaltyDisabled_doesNotConsumePoints() {
        TicketPricing p = calculator.calculate(vnd(150_000), null, 1, false, null, 0);

        assertThat(p.loyaltyPointsUsed()).isZero();
        assertThat(p.loyaltyDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** P1-26: payable PHẢI là số nguyên VND để số tiền VietQR khớp đơn hàng. */
    @Test
    void payable_roundedHalfUpToWholeVnd() {
        TicketPricing p = calculator.calculate(new BigDecimal("150000.50"), null, 1, false, null, 0);

        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(150_001));
        assertThat(p.payableAmount().scale()).isZero();
    }

    @Test
    void payable_roundedHalfUpDown() {
        TicketPricing p = calculator.calculate(new BigDecimal("150000.49"), null, 1, false, null, 0);

        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(150_000));
    }

    // ---------- V82: dịch vụ kèm vé ----------

    /** Dịch vụ cộng MỘT LẦN cho cả vé — khác phụ phí PT vốn nhân theo số ngày. */
    @Test
    void services_addedOnce_notPerDay() {
        TicketPricing p = calculator.calculate(vnd(1_000_000), vnd(200_000), 10, true,
                vnd(300_000), null, 0);

        // 1.000.000 + 200.000*10 + 300.000 (không phải 300.000*10)
        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(3_300_000));
        assertThat(p.servicesAmount()).isEqualByComparingTo(vnd(300_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(3_300_000));
    }

    /** Voucher giảm trên tổng ĐÃ gồm dịch vụ, không phải trên riêng giá vé. */
    @Test
    void services_includedBeforeVoucher() {
        TicketPricing p = calculator.calculate(vnd(500_000), null, 1, false,
                vnd(200_000), vnd(100_000), 0);

        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(700_000));
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(600_000));
    }

    /** Điểm thưởng vẫn ăn sau cùng, sau khi dịch vụ đã vào tổng. */
    @Test
    void services_thenVoucher_thenPoints() {
        TicketPricing p = calculator.calculate(vnd(500_000), null, 1, false,
                vnd(200_000), vnd(100_000), 300);

        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(700_000));
        assertThat(p.loyaltyDiscount()).isEqualByComparingTo(vnd(300_000));
        assertThat(p.loyaltyPointsUsed()).isEqualTo(300);
        assertThat(p.payableAmount()).isEqualByComparingTo(vnd(300_000));
    }

    /** Không chọn dịch vụ nào -> hành vi y hệt trước V82. */
    @Test
    void noServices_behavesAsBefore() {
        TicketPricing p = calculator.calculate(vnd(150_000), null, 1, false, null, null, 0);

        assertThat(p.servicesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.totalAmount()).isEqualByComparingTo(vnd(150_000));
    }

    @Test
    void negativeServicesAmount_rejected() {
        assertThatThrownBy(() -> calculator.calculate(vnd(150_000), null, 1, false,
                vnd(-1), null, 0))
                .isInstanceOf(BusinessException.class);
    }
}
