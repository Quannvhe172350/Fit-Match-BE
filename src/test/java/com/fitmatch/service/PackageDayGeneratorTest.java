package com.fitmatch.service;

import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.support.PackageDayGenerator;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Câu 27: đếm thẳng n ngày lịch, không nhảy cuối tuần, không né ngày nghỉ. */
class PackageDayGeneratorTest {

    private final PackageDayGenerator generator = new PackageDayGenerator();

    @Test
    void generates_consecutiveCalendarDays() {
        List<LocalDate> days = generator.generate(LocalDate.of(2026, 8, 13), 10);

        assertThat(days).hasSize(10);
        assertThat(days.get(0)).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(days.get(9)).isEqualTo(LocalDate.of(2026, 8, 22));
        for (int i = 1; i < days.size(); i++) {
            assertThat(days.get(i)).isEqualTo(days.get(i - 1).plusDays(1));
        }
    }

    /** Gói bắt đầu thứ sáu vẫn bao gồm thứ bảy và chủ nhật ngay sau đó. */
    @Test
    void doesNotSkipWeekends() {
        List<LocalDate> days = generator.generate(LocalDate.of(2026, 8, 14), 3); // 14/8/2026 = thứ sáu

        assertThat(days).extracting(LocalDate::getDayOfWeek)
                .containsExactly(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void crossesMonthBoundary() {
        List<LocalDate> days = generator.generate(LocalDate.of(2026, 8, 30), 4);

        assertThat(days).containsExactly(
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));
    }

    @Test
    void dayTicket_generatesSingleDay() {
        assertThat(generator.generate(LocalDate.of(2026, 8, 13), 1))
                .containsExactly(LocalDate.of(2026, 8, 13));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> generator.generate(null, 5))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> generator.generate(LocalDate.of(2026, 8, 13), 0))
                .isInstanceOf(BusinessException.class);
    }
}
