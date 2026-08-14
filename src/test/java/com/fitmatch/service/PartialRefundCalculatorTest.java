package com.fitmatch.service;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.support.PartialRefundCalculator;
import com.fitmatch.service.support.PartialRefundCalculator.RefundSplit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Câu 11: hoàn một phần theo số ngày đã qua. Bất biến quan trọng nhất là BẢO
 * TOÀN TIỀN — refund + retained phải đúng bằng payableAmount với mọi bộ số, kể
 * cả khi chia lẻ không hết.
 */
class PartialRefundCalculatorTest {

    private final PartialRefundCalculator calculator = new PartialRefundCalculator();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    private Ticket ticket(long payable, int dayCount, LocalDate startDate) {
        return Ticket.builder()
                .id(1L)
                .payableAmount(BigDecimal.valueOf(payable))
                .dayCount(dayCount)
                .startDate(startDate)
                .build();
    }

    /** Vé gói 10 ngày, đã qua 3 ngày -> khách nhận 7/10, gym giữ 3/10. */
    @Test
    void package10Days_elapsed3_splitsSevenThree() {
        Ticket t = ticket(1_000_000, 10, TODAY.minusDays(2)); // ngày bắt đầu tính là ngày 1

        RefundSplit split = calculator.partialElapsed(t, TODAY);

        assertThat(split.elapsedDays()).isEqualTo(3);
        assertThat(split.retained()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        assertThat(split.refund()).isEqualByComparingTo(BigDecimal.valueOf(700_000));
    }

    /** Câu 13: chưa dùng ngày nào -> hoàn 100%, admin chỉ có một lựa chọn. */
    @Test
    void notStartedYet_refundsEverything() {
        Ticket t = ticket(1_000_000, 10, TODAY.plusDays(5));

        RefundSplit split = calculator.partialElapsed(t, TODAY);

        assertThat(split.elapsedDays()).isZero();
        assertThat(split.retained()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(split.refund()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    void neverScheduled_refundsEverything() {
        Ticket t = ticket(1_000_000, 10, null);

        RefundSplit split = calculator.partialElapsed(t, TODAY);

        assertThat(split.elapsedDays()).isZero();
        assertThat(split.refund()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    /** Đã qua hết số ngày của vé -> không hoàn đồng nào, elapsed bị chặn ở dayCount. */
    @Test
    void allDaysElapsed_refundsNothing() {
        Ticket t = ticket(1_000_000, 10, TODAY.minusDays(30));

        RefundSplit split = calculator.partialElapsed(t, TODAY);

        assertThat(split.elapsedDays()).isEqualTo(10);
        assertThat(split.retained()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(split.refund()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Phương án FULL luôn hoàn hết bất kể đã tập bao nhiêu ngày. */
    @Test
    void full_refundsEverythingRegardlessOfElapsed() {
        Ticket t = ticket(1_000_000, 10, TODAY.minusDays(4));

        RefundSplit split = calculator.full(t);

        assertThat(split.retained()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(split.refund()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    /**
     * Bảo toàn tiền với số chia lẻ: 1.000.000 / 7 ngày không chia hết, phần dư
     * làm tròn PHẢI rơi về khoản hoàn cho khách chứ không bốc hơi.
     */
    @Test
    void moneyConserved_forEveryCombination() {
        long[] payables = {1_000_000, 999_999, 350_000, 1, 123_457, 7};
        for (long payable : payables) {
            for (int dayCount = 1; dayCount <= 12; dayCount++) {
                for (int elapsed = 0; elapsed <= dayCount; elapsed++) {
                    LocalDate start = elapsed == 0
                            ? TODAY.plusDays(1)
                            : TODAY.minusDays(elapsed - 1L);
                    Ticket t = ticket(payable, dayCount, start);

                    RefundSplit split = calculator.partialElapsed(t, TODAY);

                    assertThat(split.refund().add(split.retained()))
                            .as("payable=%d dayCount=%d elapsed=%d", payable, dayCount, elapsed)
                            .isEqualByComparingTo(BigDecimal.valueOf(payable));
                    assertThat(split.refund()).as("refund không âm").isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    assertThat(split.retained()).as("retained không âm").isGreaterThanOrEqualTo(BigDecimal.ZERO);
                }
            }
        }
    }

    /** Vé ngày (dayCount = 1) chỉ có hai kết cục: hoàn hết hoặc giữ hết. */
    @Test
    void dayTicket_isAllOrNothing() {
        Ticket notStarted = ticket(150_000, 1, TODAY.plusDays(1));
        assertThat(calculator.partialElapsed(notStarted, TODAY).refund())
                .isEqualByComparingTo(BigDecimal.valueOf(150_000));

        Ticket started = ticket(150_000, 1, TODAY);
        assertThat(calculator.partialElapsed(started, TODAY).refund())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
