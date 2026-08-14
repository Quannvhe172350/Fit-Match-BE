package com.fitmatch.service;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.support.RefundRequestWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Bug S2-09: cửa sổ gửi yêu cầu hoàn tiền neo vào NGÀY TẬP ĐẦU TIÊN — cùng mốc
 * PartialRefundCalculator dùng để đếm ngày đã dùng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundRequestWindowTest {

    @Mock private SystemConfigService systemConfigService;
    private RefundRequestWindow window;

    @BeforeEach
    void setUp() {
        window = new RefundRequestWindow(systemConfigService);
        ReflectionTestUtils.setField(window, "defaultWindowDays", 7);
        // Mock trả 0L cho kiểu Long chứ không trả null, mà 0 ở đây mang nghĩa
        // "không giới hạn" — phải stub tay để test đúng nhánh mặc định.
        when(systemConfigService.findLong(RefundRequestWindow.CONFIG_KEY)).thenReturn(null);
    }

    private Ticket ticket(LocalDate startDate) {
        return Ticket.builder().id(10L).dayCount(10).startDate(startDate).build();
    }

    @Test
    void windowDays_prefersRuntimeConfigOverDefault() {
        when(systemConfigService.findLong(RefundRequestWindow.CONFIG_KEY)).thenReturn(14L);

        assertThat(window.windowDays()).isEqualTo(14);
    }

    /** Vé chưa xếp lịch: chưa nhận gì nên chưa tính giờ — và vẫn được hoàn 100%. */
    @Test
    void unscheduledTicket_hasNoDeadline() {
        Ticket t = ticket(null);

        assertThat(window.deadline(t)).isNull();
        assertThat(window.expired(t)).isFalse();
    }

    /** Vé đặt lịch cho tuần sau: hạn đếm từ ngày bắt đầu, chưa chạy. */
    @Test
    void futureStartDate_isNotExpired() {
        Ticket t = ticket(LocalDate.now().plusDays(5));

        assertThat(window.deadline(t)).isEqualTo(LocalDate.now().plusDays(12));
        assertThat(window.expired(t)).isFalse();
    }

    @Test
    void withinWindow_isAllowed() {
        assertThat(window.expired(ticket(LocalDate.now().minusDays(7)))).isFalse();
    }

    @Test
    void afterWindow_isExpired() {
        assertThat(window.expired(ticket(LocalDate.now().minusDays(8)))).isTrue();
    }

    @Test
    void unlimitedWindow_neverExpires() {
        when(systemConfigService.findLong(RefundRequestWindow.CONFIG_KEY)).thenReturn(0L);

        Ticket t = ticket(LocalDate.now().minusDays(365));
        assertThat(window.deadline(t)).isNull();
        assertThat(window.expired(t)).isFalse();
    }
}
