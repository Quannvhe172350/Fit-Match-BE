package com.fitmatch.service.support;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cửa sổ thời gian mở tranh chấp (UC-063) — nguồn sự thật DUY NHẤT cho cả hai
 * phía của cùng một quyết định: bên mở tranh chấp và bên giải ngân tiền.
 *
 * <p><b>Mốc neo là vé</b>, cụ thể là {@code settlementPendingAt} — thời điểm vé
 * được chốt đã hoàn thành (USED_UP) hoặc hết hạn (EXPIRED) và tiền chuyển sang
 * chờ giải ngân. Neo vào đây chứ không vào ngày buổi tập vì đó cũng chính là mốc
 * {@code SettlementReleaseJob} dùng để đếm {@code settlementHoldDays}: hai con số
 * đo từ cùng một điểm thì "hết hạn khiếu nại" và "tiền rời khỏi hệ thống" không
 * bao giờ lệch pha. Tranh chấp cấp buổi cũng dùng hạn của vé — một vé một hạn.
 *
 * <p>Vé chưa có mốc (còn đang dùng, hoặc vé 0đ do điểm/voucher phủ hết nên không
 * qua bước chờ giải ngân) thì KHÔNG có hạn: chưa có gì kết toán để mà chốt sổ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeWindow {

    public static final String CONFIG_KEY = "dispute.open-window-days";

    private final SystemConfigService systemConfigService;

    /** Fallback khi system_configs chưa có key — phải khớp settlementHoldDays. */
    @Value("${app.dispute.open-window-days:7}")
    private int defaultWindowDays;

    /** Số ngày hiệu lực: DB override trước, rồi tới env/default. {@code <= 0} = không giới hạn. */
    public int windowDays() {
        Long configured = systemConfigService.findLong(CONFIG_KEY);
        return configured != null ? configured.intValue() : defaultWindowDays;
    }

    /** Thời điểm hết quyền mở tranh chấp; null = chưa có hạn (xem javadoc lớp). */
    public LocalDateTime deadlineAt(Ticket ticket) {
        int days = windowDays();
        if (days <= 0 || ticket.getSettlementPendingAt() == null) {
            return null;
        }
        return ticket.getSettlementPendingAt().plusDays(days);
    }

    /** Hạn dạng ngày để trả ra FE ("còn N ngày để mở tranh chấp"). */
    public LocalDate deadline(Ticket ticket) {
        LocalDateTime at = deadlineAt(ticket);
        return at != null ? at.toLocalDate() : null;
    }

    /** Đã hết quyền mở tranh chấp. Không có hạn = không bao giờ hết. */
    public boolean expired(Ticket ticket) {
        LocalDateTime at = deadlineAt(ticket);
        return at != null && LocalDateTime.now().isAfter(at);
    }

    /**
     * Cửa sổ khiếu nại đã đóng chưa, theo góc nhìn của {@code SettlementReleaseJob}.
     *
     * <p>Khác {@link #expired} ở đúng một chỗ và đó là chỗ quan trọng: cấu hình
     * "không giới hạn" ({@code <= 0}) nghĩa là khách mở tranh chấp lúc nào cũng
     * được, nhưng KHÔNG có nghĩa là hoãn trả tiền gym vô thời hạn. Dùng chung một
     * hàm cho cả hai câu hỏi là cách khoá sạch mọi lần giải ngân bằng một dòng
     * config.
     */
    public boolean releaseAllowed(Ticket ticket) {
        return windowDays() <= 0 || expired(ticket);
    }
}
