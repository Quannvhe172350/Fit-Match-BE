package com.fitmatch.service;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketStatusHistory;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TicketStatusHistoryRepository;
import com.fitmatch.service.support.TicketLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** State machine của vé — đặc biệt là ngõ cụt EXPIRED (câu 32). */
@ExtendWith(MockitoExtension.class)
class TicketLifecycleTest {

    @Mock private TicketStatusHistoryRepository historyRepository;
    @InjectMocks private TicketLifecycle lifecycle;

    private Ticket ticket(TicketStatus status) {
        return Ticket.builder().id(7L).status(status).build();
    }

    @Test
    void pendingPayment_toActive_allowed() {
        Ticket t = ticket(TicketStatus.PENDING_PAYMENT);

        lifecycle.transition(t, TicketStatus.ACTIVE, "Đã nhận thanh toán");

        assertThat(t.getStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(t.getStatusReason()).isEqualTo("Đã nhận thanh toán");
    }

    @Test
    void active_toUsedUpExpiredRefunded_allowed() {
        for (TicketStatus to : new TicketStatus[]{
                TicketStatus.USED_UP, TicketStatus.EXPIRED, TicketStatus.REFUNDED}) {
            Ticket t = ticket(TicketStatus.ACTIVE);
            lifecycle.transition(t, to, "test");
            assertThat(t.getStatus()).isEqualTo(to);
        }
    }

    /** Câu 32: vé hết hạn thì tiền đã về gym — không được hoàn ngược nữa. */
    @Test
    void expired_toRefunded_rejected() {
        Ticket t = ticket(TicketStatus.EXPIRED);

        assertThatThrownBy(() -> lifecycle.transition(t, TicketStatus.REFUNDED, "khách đòi hoàn"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot move ticket from EXPIRED to REFUNDED");
        assertThat(t.getStatus()).isEqualTo(TicketStatus.EXPIRED);
    }

    @Test
    void pendingPayment_toUsedUp_rejected() {
        Ticket t = ticket(TicketStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> lifecycle.transition(t, TicketStatus.USED_UP, "bỏ qua thanh toán"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void terminalStates_haveNoOutgoingTransition() {
        for (TicketStatus terminal : new TicketStatus[]{
                TicketStatus.USED_UP, TicketStatus.EXPIRED,
                TicketStatus.CANCELLED, TicketStatus.REFUNDED}) {
            for (TicketStatus to : TicketStatus.values()) {
                assertThat(lifecycle.canTransition(terminal, to))
                        .as(terminal + " -> " + to).isFalse();
            }
        }
    }

    @Test
    void eachTransition_writesExactlyOneHistoryRow() {
        Ticket t = ticket(TicketStatus.PENDING_PAYMENT);

        lifecycle.transition(t, TicketStatus.ACTIVE, "paid");
        lifecycle.transition(t, TicketStatus.USED_UP, "dùng hết");

        ArgumentCaptor<TicketStatusHistory> captor = ArgumentCaptor.forClass(TicketStatusHistory.class);
        verify(historyRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TicketStatusHistory::getFromStatus)
                .containsExactly(TicketStatus.PENDING_PAYMENT, TicketStatus.ACTIVE);
        assertThat(captor.getAllValues()).extracting(TicketStatusHistory::getToStatus)
                .containsExactly(TicketStatus.ACTIVE, TicketStatus.USED_UP);
    }

    /** Chuyển sai luồng KHÔNG được để lại dòng history nào. */
    @Test
    void rejectedTransition_writesNoHistory() {
        Ticket t = ticket(TicketStatus.CANCELLED);

        assertThatThrownBy(() -> lifecycle.transition(t, TicketStatus.ACTIVE, "hồi sinh"))
                .isInstanceOf(BusinessException.class);

        verify(historyRepository, times(0)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordNote_keepsStatusAndWritesFromEqualsTo() {
        Ticket t = ticket(TicketStatus.ACTIVE);

        lifecycle.recordNote(t, "admin ghi chú");

        ArgumentCaptor<TicketStatusHistory> captor = ArgumentCaptor.forClass(TicketStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(captor.getValue().getToStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.ACTIVE);
    }
}
