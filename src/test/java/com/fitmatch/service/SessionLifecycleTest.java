package com.fitmatch.service;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.SessionStatusHistory;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.SessionStatusHistoryRepository;
import com.fitmatch.service.support.SessionLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * State machine của buổi tập. Điểm cần khoá lại: dời lịch / đổi khung PT KHÔNG
 * đổi trạng thái nhưng vẫn phải để lại dấu vết.
 */
@ExtendWith(MockitoExtension.class)
class SessionLifecycleTest {

    @Mock private SessionStatusHistoryRepository historyRepository;
    @InjectMocks private SessionLifecycle lifecycle;

    private TrainingSession session(SessionStatus status) {
        return TrainingSession.builder()
                .id(3L).status(status).sessionDate(LocalDate.of(2026, 8, 20)).build();
    }

    @Test
    void scheduled_toDone_allowed() {
        TrainingSession s = session(SessionStatus.SCHEDULED);

        lifecycle.transition(s, SessionStatus.DONE, "ngày tập đã qua");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void scheduled_toCancelled_allowed() {
        TrainingSession s = session(SessionStatus.SCHEDULED);

        lifecycle.transition(s, SessionStatus.CANCELLED, "vé được hoàn tiền");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void done_toAnything_rejected() {
        TrainingSession s = session(SessionStatus.DONE);

        assertThatThrownBy(() -> lifecycle.transition(s, SessionStatus.CANCELLED, "huỷ muộn"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot move session from DONE to CANCELLED");
    }

    @Test
    void cancelled_toScheduled_rejected() {
        TrainingSession s = session(SessionStatus.CANCELLED);

        assertThatThrownBy(() -> lifecycle.transition(s, SessionStatus.SCHEDULED, "đặt lại"))
                .isInstanceOf(BusinessException.class);
    }

    /** Dời ngày: status giữ nguyên SCHEDULED nhưng phải có một dòng history. */
    @Test
    void reschedule_keepsStatus_butRecordsHistory() {
        TrainingSession s = session(SessionStatus.SCHEDULED);
        s.setSessionDate(LocalDate.of(2026, 8, 25));

        lifecycle.recordNote(s, "Dời lịch 2026-08-20 -> 2026-08-25");

        ArgumentCaptor<SessionStatusHistory> captor = ArgumentCaptor.forClass(SessionStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(captor.getValue().getToStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(captor.getValue().getReason()).contains("Dời lịch");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    /** Không còn NO_SHOW: buổi tiêu theo ngày bất kể khách có mặt hay không (câu 9). */
    @Test
    void statusSet_hasNoNoShow() {
        assertThat(SessionStatus.values())
                .containsExactlyInAnyOrder(
                        SessionStatus.SCHEDULED, SessionStatus.DONE, SessionStatus.CANCELLED);
    }
}
