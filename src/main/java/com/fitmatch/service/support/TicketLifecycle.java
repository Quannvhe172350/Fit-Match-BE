package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketStatusHistory;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TicketStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.fitmatch.common.enums.TicketStatus.ACTIVE;
import static com.fitmatch.common.enums.TicketStatus.CANCELLED;
import static com.fitmatch.common.enums.TicketStatus.EXPIRED;
import static com.fitmatch.common.enums.TicketStatus.PENDING_PAYMENT;
import static com.fitmatch.common.enums.TicketStatus.REFUNDED;
import static com.fitmatch.common.enums.TicketStatus.USED_UP;

/**
 * State machine tập trung của vé: mọi chuyển trạng thái phải đi qua
 * {@link #transition} để được kiểm tra tính hợp lệ và ghi lịch sử. Không nơi
 * nào khác được gọi {@code ticket.setStatus(...)} trực tiếp.
 *
 * <p>Ba trạng thái kết thúc đều là ngõ cụt. Đáng chú ý nhất là EXPIRED: câu 32
 * quy định vé hết hạn thì tiền tự về gym và khách KHÔNG hoàn được nữa, nên
 * EXPIRED -> REFUNDED bị chặn ngay ở bảng chuyển thay vì kiểm tra rải rác
 * trong RefundService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketLifecycle {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, Set.of(ACTIVE, CANCELLED),
            ACTIVE, Set.of(USED_UP, EXPIRED, REFUNDED),
            USED_UP, Set.of(),
            EXPIRED, Set.of(),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

    private final TicketStatusHistoryRepository historyRepository;

    /** Chuyển trạng thái nếu hợp lệ; ghi history; ném 409 nếu chuyển sai luồng. */
    public void transition(Ticket ticket, TicketStatus to, String reason) {
        TicketStatus from = ticket.getStatus();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot move ticket from " + from + " to " + to);
        }
        ticket.setStatus(to);
        ticket.setStatusReason(reason);
        historyRepository.save(TicketStatusHistory.builder()
                .ticket(ticket)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build());
        log.info("Ticket {} moved {} -> {} ({})", ticket.getId(), from, to, reason);
    }

    /** Ghi một dòng lịch sử không đổi trạng thái (vd đổi chi nhánh liên hệ, ghi chú admin). */
    public void recordNote(Ticket ticket, String reason) {
        historyRepository.save(TicketStatusHistory.builder()
                .ticket(ticket)
                .fromStatus(ticket.getStatus())
                .toStatus(ticket.getStatus())
                .reason(reason)
                .build());
        log.info("Ticket {} note: {}", ticket.getId(), reason);
    }

    /** Cho phép caller hỏi trước thay vì bắt exception (vd ẩn nút ở API trả về). */
    public boolean canTransition(TicketStatus from, TicketStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
