package com.fitmatch.service;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.service.support.DisputeFinancialApplier;
import com.fitmatch.service.support.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UC-067: mọi kết cục thao tác trên phần ĐANG ĐÓNG BĂNG (frozenAmount).
 * Bất biến: refund + phần về gym = frozenAmount, không đồng nào bốc hơi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeFinancialApplierTest {

    private static final Long TICKET_ID = 10L;
    private static final Long GYM_ID = 1L;

    @Mock private WalletService walletService;
    @Mock private TicketRepository ticketRepository;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private DisputeFinancialApplier applier;

    private Ticket ticket(SettlementStatus settlement) {
        return Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username("customer1").build())
                .gymProfile(GymProfile.builder().id(GYM_ID).gymName("Gym A").build())
                .dayCount(10)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .settlementStatus(settlement)
                .build();
    }

    private Dispute dispute(Ticket t, BigDecimal frozen) {
        return Dispute.builder().id(1L).ticket(t).frozenAmount(frozen).build();
    }

    @Test
    void refundFull_returnsEverythingToCustomer() {
        Ticket t = ticket(SettlementStatus.DISPUTED);

        applier.apply(dispute(t, BigDecimal.valueOf(1_000_000)), DisputeResolution.REFUND_FULL, null);

        verify(walletService).refundToCustomerForTicket(eq(GYM_ID), any(), eq(TICKET_ID),
                eq(BigDecimal.valueOf(1_000_000)));
        verify(walletService, never()).moveToPendingForTicket(any(), any(), any());
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED);
    }

    @Test
    void releaseToGym_movesEverythingToPending() {
        Ticket t = ticket(SettlementStatus.DISPUTED);

        applier.apply(dispute(t, BigDecimal.valueOf(1_000_000)), DisputeResolution.RELEASE_TO_GYM, null);

        verify(walletService).moveToPendingForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(1_000_000));
        verify(walletService, never()).refundToCustomerForTicket(any(), any(), any(), any());
        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING_RELEASE);
    }

    @Test
    void split_conservesMoney() {
        Ticket t = ticket(SettlementStatus.DISPUTED);

        applier.apply(dispute(t, BigDecimal.valueOf(1_000_000)), DisputeResolution.SPLIT,
                BigDecimal.valueOf(400_000));

        verify(walletService).refundToCustomerForTicket(eq(GYM_ID), any(), eq(TICKET_ID),
                eq(BigDecimal.valueOf(400_000)));
        verify(walletService).moveToPendingForTicket(GYM_ID, TICKET_ID, BigDecimal.valueOf(600_000));
    }

    @Test
    void split_refundAboveFrozen_isRejected() {
        Ticket t = ticket(SettlementStatus.DISPUTED);

        assertThatThrownBy(() -> applier.apply(dispute(t, BigDecimal.valueOf(1_000_000)),
                DisputeResolution.SPLIT, BigDecimal.valueOf(2_000_000)))
                .isInstanceOf(BusinessException.class);
        verify(walletService, never()).refundToCustomerForTicket(any(), any(), any(), any());
    }

    /**
     * Câu 34: tranh chấp cấp buổi chỉ đóng băng giá trị một ngày, nên hoàn toàn
     * bộ ở đây cũng chỉ là 1/10 vé — phần còn lại của vé không bị đụng.
     */
    @Test
    void sessionLevelDispute_onlyTouchesOneDayValue() {
        Ticket t = ticket(SettlementStatus.DISPUTED);

        applier.apply(dispute(t, BigDecimal.valueOf(100_000)), DisputeResolution.REFUND_FULL, null);

        verify(walletService).refundToCustomerForTicket(eq(GYM_ID), any(), eq(TICKET_ID),
                eq(BigDecimal.valueOf(100_000)));
    }

    /** P1-5: tiền đã giải ngân xong thì không tự đòi lại được — cần thu hồi thủ công. */
    @Test
    void refundAfterRelease_isRejectedInsteadOfSilentlyFailing() {
        Ticket t = ticket(SettlementStatus.RELEASED);

        assertThatThrownBy(() -> applier.apply(dispute(t, BigDecimal.ZERO),
                DisputeResolution.REFUND_FULL, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("thu hồi thủ công");
    }

    /** NO_ACTION trên tiền đã kết toán: giữ nguyên trạng thái, không đè RELEASED. */
    @Test
    void noActionAfterRelease_keepsSettledStatus() {
        Ticket t = ticket(SettlementStatus.RELEASED);

        applier.apply(dispute(t, BigDecimal.ZERO), DisputeResolution.NO_ACTION, null);

        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.RELEASED);
    }

    /** Vé miễn phí (điểm/voucher phủ hết): chỉ ghi nhận quyết định, không có tiền. */
    @Test
    void freeTicket_recordsDecisionWithoutMoney() {
        Ticket t = ticket(SettlementStatus.NONE);

        applier.apply(dispute(t, BigDecimal.ZERO), DisputeResolution.NO_ACTION, null);

        assertThat(t.getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
        verify(walletService, never()).refundToCustomerForTicket(any(), any(), any(), any());
    }
}
