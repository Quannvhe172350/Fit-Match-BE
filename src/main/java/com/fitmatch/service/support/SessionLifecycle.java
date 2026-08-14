package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.SessionStatusHistory;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.SessionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.fitmatch.common.enums.SessionStatus.CANCELLED;
import static com.fitmatch.common.enums.SessionStatus.DONE;
import static com.fitmatch.common.enums.SessionStatus.SCHEDULED;

/**
 * State machine của một ngày tập — tách hẳn khỏi {@link TicketLifecycle} vì vé
 * và buổi tập là hai dòng đời độc lập (vé nói về tiền, buổi nói về lịch).
 *
 * <p>Cố ý rất hẹp. Dời lịch, đổi khung giờ PT, thêm hoặc bỏ PT đều KHÔNG đổi
 * trạng thái: caller sửa tại chỗ rồi gọi {@link #recordNote} để vẫn có dấu vết.
 * SCHEDULED -> DONE do SessionCompletionJob quyết theo ngày, không do check-in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionLifecycle {

    private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED = Map.of(
            SCHEDULED, Set.of(DONE, CANCELLED),
            DONE, Set.of(),
            CANCELLED, Set.of()
    );

    private final SessionStatusHistoryRepository historyRepository;

    /** Chuyển trạng thái nếu hợp lệ; ghi history; ném 409 nếu chuyển sai luồng. */
    public void transition(TrainingSession session, SessionStatus to, String reason) {
        SessionStatus from = session.getStatus();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot move session from " + from + " to " + to);
        }
        session.setStatus(to);
        session.setStatusReason(reason);
        historyRepository.save(SessionStatusHistory.builder()
                .session(session)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build());
        log.info("Session {} moved {} -> {} ({})", session.getId(), from, to, reason);
    }

    /**
     * Ghi một dòng lịch sử không đổi trạng thái: dời ngày (vé DAY), đổi khung giờ
     * PT, bổ sung PT cho ngày còn trống, gỡ PT, gym xác nhận PT có đến, khách
     * check-in. Đây là đường duy nhất để những thay đổi đó để lại dấu vết.
     */
    public void recordNote(TrainingSession session, String reason) {
        historyRepository.save(SessionStatusHistory.builder()
                .session(session)
                .fromStatus(session.getStatus())
                .toStatus(session.getStatus())
                .reason(reason)
                .build());
        log.info("Session {} note: {}", session.getId(), reason);
    }

    public boolean canTransition(SessionStatus from, SessionStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
