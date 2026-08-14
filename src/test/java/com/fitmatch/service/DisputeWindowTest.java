package com.fitmatch.service;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.support.DisputeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * D-18: cửa sổ mở tranh chấp neo vào {@code settlementPendingAt} của VÉ — cùng
 * mốc mà SettlementReleaseJob đếm settlementHoldDays, để "hết hạn khiếu nại" và
 * "tiền về gym" không lệch pha.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeWindowTest {

    @Mock private SystemConfigService systemConfigService;
    private DisputeWindow window;

    @BeforeEach
    void setUp() {
        window = new DisputeWindow(systemConfigService);
        ReflectionTestUtils.setField(window, "defaultWindowDays", 7);
        // Mặc định: chưa có key trong system_configs -> rơi về default. Phải stub
        // TAY vì mock trả 0L cho kiểu Long chứ không trả null, và 0 ở đây mang
        // nghĩa hoàn toàn khác ("không giới hạn").
        when(systemConfigService.findLong(DisputeWindow.CONFIG_KEY)).thenReturn(null);
    }

    private Ticket ticket(LocalDateTime pendingAt) {
        return Ticket.builder().id(10L).settlementPendingAt(pendingAt).build();
    }

    /** Giá trị trong system_configs thắng env/default — đó là điểm của UC-078. */
    @Test
    void windowDays_prefersRuntimeConfigOverDefault() {
        when(systemConfigService.findLong(DisputeWindow.CONFIG_KEY)).thenReturn(30L);

        assertThat(window.windowDays()).isEqualTo(30);
    }

    @Test
    void windowDays_fallsBackToDefaultWhenKeyMissing() {
        when(systemConfigService.findLong(DisputeWindow.CONFIG_KEY)).thenReturn(null);

        assertThat(window.windowDays()).isEqualTo(7);
    }

    /** Vé chưa kết toán: chưa có gì chốt sổ nên chưa có hạn khiếu nại. */
    @Test
    void noSettlementAnchor_meansNoDeadline() {
        Ticket t = ticket(null);

        assertThat(window.deadline(t)).isNull();
        assertThat(window.expired(t)).isFalse();
    }

    @Test
    void deadline_isAnchorPlusWindow() {
        Ticket t = ticket(LocalDateTime.now().minusDays(2));

        assertThat(window.deadline(t)).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(window.expired(t)).isFalse();
    }

    @Test
    void expired_afterWindowElapsed() {
        Ticket t = ticket(LocalDateTime.now().minusDays(8));

        assertThat(window.expired(t)).isTrue();
        assertThat(window.releaseAllowed(t)).isTrue();
    }

    /** Còn trong cửa sổ thì tiền chưa được rời khỏi hệ thống. */
    @Test
    void releaseNotAllowed_whileWindowOpen() {
        Ticket t = ticket(LocalDateTime.now().minusDays(1));

        assertThat(window.releaseAllowed(t)).isFalse();
    }

    /**
     * Cấu hình "không giới hạn" (<= 0) cho khách mở tranh chấp bất cứ lúc nào,
     * nhưng KHÔNG được hoãn trả tiền gym vĩnh viễn — nếu không, một dòng config
     * khoá sạch mọi lần giải ngân.
     */
    @Test
    void unlimitedWindow_stillAllowsRelease() {
        when(systemConfigService.findLong(DisputeWindow.CONFIG_KEY)).thenReturn(0L);
        Ticket t = ticket(LocalDateTime.now().minusDays(1));

        assertThat(window.deadline(t)).isNull();
        assertThat(window.expired(t)).isFalse();
        assertThat(window.releaseAllowed(t)).isTrue();
    }
}
