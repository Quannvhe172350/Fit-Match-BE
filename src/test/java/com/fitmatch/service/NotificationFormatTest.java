package com.fitmatch.service;

import com.fitmatch.service.support.NotificationFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sheet 7 mục 1: thông báo viết theo cách người Việt đọc.
 *
 * <p>Trước đây thông báo nối thẳng đối tượng vào chuỗi nên khách nhận được
 * "Buổi ngày 2026-09-20" và "hoàn lại 50000.00 đ".
 */
class NotificationFormatTest {

    @Test
    void date_isDayMonthYear() {
        assertThat(NotificationFormat.date(LocalDate.of(2026, 9, 20))).isEqualTo("20/09/2026");
    }

    @Test
    void time_dropsSeconds() {
        assertThat(NotificationFormat.time(LocalTime.of(19, 0, 30))).isEqualTo("19:00");
    }

    @Test
    void dateTime_joinsBoth() {
        assertThat(NotificationFormat.dateTime(LocalDateTime.of(2026, 9, 20, 19, 5)))
                .isEqualTo("20/09/2026 19:05");
    }

    /** Hạn dùng vé lưu cả giờ nhưng chỉ NGÀY mới có ý nghĩa với người đọc. */
    @Test
    void dateOf_keepsOnlyTheDay() {
        assertThat(NotificationFormat.dateOf(LocalDateTime.of(2026, 9, 20, 23, 59)))
                .isEqualTo("20/09/2026");
    }

    /** Dấu chấm phân nhóm và ký hiệu ₫ — khớp formatCurrency của FE. */
    @Test
    void money_groupsThousandsAndAppendsDong() {
        assertThat(NotificationFormat.money(BigDecimal.valueOf(1_000_000))).isEqualTo("1.000.000 ₫");
        assertThat(NotificationFormat.money(BigDecimal.valueOf(50_000))).isEqualTo("50.000 ₫");
        assertThat(NotificationFormat.money(BigDecimal.ZERO)).isEqualTo("0 ₫");
    }

    /** VND không có đơn vị nhỏ hơn đồng: phần lẻ được làm tròn, không in ra. */
    @Test
    void money_roundsToWholeDong() {
        assertThat(NotificationFormat.money(new BigDecimal("50000.00"))).isEqualTo("50.000 ₫");
        assertThat(NotificationFormat.money(new BigDecimal("1234.56"))).isEqualTo("1.235 ₫");
    }

    @Test
    void percent_dropsTrailingZeros() {
        assertThat(NotificationFormat.percent(new BigDecimal("50.00"))).isEqualTo("50");
        assertThat(NotificationFormat.percent(new BigDecimal("30.50"))).isEqualTo("30.5");
        assertThat(NotificationFormat.percent(BigDecimal.ZERO)).isEqualTo("0");
    }

    /**
     * Null ra chuỗi rỗng chứ không ném: thông báo là đường phụ, một trường trống
     * không được phép làm hỏng giao dịch nghiệp vụ đứng trước nó.
     */
    @Test
    void nulls_renderEmpty() {
        assertThat(NotificationFormat.date(null)).isEmpty();
        assertThat(NotificationFormat.time(null)).isEmpty();
        assertThat(NotificationFormat.dateTime(null)).isEmpty();
        assertThat(NotificationFormat.dateOf(null)).isEmpty();
        assertThat(NotificationFormat.money(null)).isEmpty();
        assertThat(NotificationFormat.percent(null)).isEmpty();
    }
}
