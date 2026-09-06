package com.fitmatch.service;

import com.fitmatch.entity.GymPolicy;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.repository.GymPolicyRepository;
import com.fitmatch.service.support.SessionCancelRefundCalculator;
import com.fitmatch.service.support.SessionCancelRefundCalculator.Quote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * V94: huỷ một ngày tập được hoàn bao nhiêu.
 *
 * <p>Bất biến quan trọng nhất: tổng hoàn KHÔNG BAO GIỜ vượt phần vé còn hoàn
 * được. Wallet chỉ kiểm held_balance tổng của gym nên không bắt được vi phạm
 * này — trần phải nằm ở đây.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionCancelRefundCalculatorTest {

    private static final LocalDate SESSION_DAY = LocalDate.of(2026, 9, 20);
    private static final Long GYM_ID = 3L;

    @Mock private GymPolicyRepository gymPolicyRepository;

    private SessionCancelRefundCalculator calculator() {
        return new SessionCancelRefundCalculator(gymPolicyRepository);
    }

    private Ticket ticket(long payable, int dayCount) {
        return Ticket.builder()
                .id(1L)
                .payableAmount(BigDecimal.valueOf(payable))
                .dayCount(dayCount)
                .ptRefundedAmount(BigDecimal.ZERO)
                .sessionRefundedAmount(BigDecimal.ZERO)
                .gymProfile(GymProfile.builder().id(GYM_ID).build())
                .build();
    }

    private TrainingSession session(Ticket ticket, LocalTime slotStart) {
        return TrainingSession.builder()
                .id(9L).ticket(ticket).sessionDate(SESSION_DAY).ptSlotStart(slotStart).build();
    }

    private void policy(Integer fullHours, Integer partialHours, String partialPercent) {
        when(gymPolicyRepository.findByGymProfile_Id(anyLong())).thenReturn(Optional.of(
                GymPolicy.builder()
                        .cancelFullRefundHours(fullHours)
                        .cancelPartialRefundHours(partialHours)
                        .cancelPartialRefundPercent(new BigDecimal(partialPercent))
                        .build()));
    }

    private void noPolicy() {
        when(gymPolicyRepository.findByGymProfile_Id(anyLong())).thenReturn(Optional.empty());
    }

    /** Vé gói 10 ngày, 1tr -> một ngày đáng 100k. Báo trước 2 ngày = hoàn đủ. */
    @Test
    void wellAhead_refundsFullDayValue() {
        noPolicy();
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.minusDays(2).atTime(19, 0));

        assertThat(quote.percent()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(quote.perDayValue()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    /** Mặc định 24h/12h: sáng cùng ngày cho buổi tối là 13 tiếng -> bậc giữa, 50%. */
    @Test
    void insidePartialWindow_refundsPartialPercent() {
        noPolicy();
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.atTime(6, 0));

        assertThat(quote.hoursAhead()).isEqualTo(13);
        assertThat(quote.percent()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    /** Sát giờ tập thì không còn gì để hoàn. */
    @Test
    void tooLate_refundsNothing() {
        noPolicy();
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.atTime(17, 0));

        assertThat(quote.percent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Mốc tính tới ĐẦU BUỔI, không phải đầu ngày. Huỷ lúc 14h cho buổi 19h cùng
     * ngày là còn 5 tiếng — tính từ 00:00 sẽ ra "đã trễ 14 tiếng" và cướp mất
     * của khách một bậc hoàn tiền họ có quyền hưởng.
     */
    @Test
    void hoursMeasuredToSlotStart_notMidnight() {
        policy(24, 4, "50.00");
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.atTime(14, 0));

        assertThat(quote.hoursAhead()).isEqualTo(5);
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    /** Buổi tự tập không có khung giờ -> mốc là 00:00, vé dùng được từ lúc mở cửa. */
    @Test
    void sessionWithoutSlot_measuresFromMidnight() {
        policy(24, 12, "50.00");
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, null), SESSION_DAY.minusDays(1).atTime(20, 0));

        assertThat(quote.hoursAhead()).isEqualTo(4);
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Gym tự đặt luật: 48h/100%, 24h/30%. */
    @Test
    void usesGymConfiguredTiers() {
        policy(48, 24, "30.00");
        Ticket t = ticket(1_000_000, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.minusDays(1).atTime(19, 0));

        assertThat(quote.percent()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
    }

    /**
     * Vé DAY: một ngày là CẢ vé. Voucher/điểm đã giảm giá nên hoàn theo số thực
     * trả, không theo giá niêm yết.
     */
    @Test
    void dayTicket_oneDayIsTheWholePayable() {
        noPolicy();
        Ticket t = ticket(180_000, 1);

        Quote quote = calculator().quote(session(t, LocalTime.of(8, 0)),
                SESSION_DAY.minusDays(3).atTime(8, 0));

        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
    }

    /**
     * Trần cuối: đã hoàn lẻ gần hết vé thì lần huỷ này chỉ còn lấy được phần dư.
     * Không có trần này, tổng hoàn vượt số khách đã trả.
     */
    @Test
    void neverRefundsMoreThanWhatIsLeft() {
        noPolicy();
        Ticket t = ticket(1_000_000, 10);
        t.setPtRefundedAmount(BigDecimal.valueOf(900_000));
        t.setSessionRefundedAmount(BigDecimal.valueOf(70_000));

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.minusDays(5).atTime(19, 0));

        // Một ngày đáng 100k nhưng vé chỉ còn 30k chưa hoàn.
        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
    }

    /** Voucher/điểm phủ hết vé -> không còn gì để hoàn, và không được ra số âm. */
    @Test
    void fullyDiscountedTicket_refundsZero() {
        noPolicy();
        Ticket t = ticket(0, 10);

        Quote quote = calculator().quote(session(t, LocalTime.of(19, 0)),
                SESSION_DAY.minusDays(5).atTime(19, 0));

        assertThat(quote.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
