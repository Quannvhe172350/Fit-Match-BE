package com.fitmatch.service;

import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.entity.LoyaltyAccount;
import com.fitmatch.entity.LoyaltyTransaction;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.LoyaltyAccountRepository;
import com.fitmatch.repository.LoyaltyTransactionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.LoyaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Câu 35: điểm được TÍCH khi thanh toán vé thành công, không phải khi hoàn tất
 * buổi tập. Câu 14: điểm bị TIÊU ngay lúc mua.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyServiceImplTest {

    private static final String USERNAME = "customer1";

    @Mock private LoyaltyAccountRepository accountRepository;
    @Mock private LoyaltyTransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private LoyaltyServiceImpl service;

    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        account = LoyaltyAccount.builder()
                .id(1L).user(User.builder().id(9L).username(USERNAME).build())
                .pointsBalance(100)
                .build();
        when(accountRepository.findByUser_Username(USERNAME)).thenReturn(Optional.of(account));
        when(accountRepository.lockById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ticket ticket(BigDecimal payable, Integer pointsUsed) {
        return Ticket.builder()
                .id(10L)
                .customer(User.builder().id(9L).username(USERNAME).build())
                .payableAmount(payable)
                .loyaltyPointsUsed(pointsUsed)
                .build();
    }

    @Test
    void availablePoints_returnsBalance() {
        assertThat(service.availablePoints(USERNAME)).isEqualTo(100);
    }

    /** 10.000đ chi tiêu = 1 điểm. */
    @Test
    void earnFromTicket_creditsOnePointPer10k() {
        service.earnFromTicket(ticket(BigDecimal.valueOf(250_000), null));

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LoyaltyTxnType.EARN);
        assertThat(captor.getValue().getPoints()).isEqualTo(25);
        // V73: bút toán neo vào vé, không còn cột booking_id.
        assertThat(captor.getValue().getTicketId()).isEqualTo(10L);
        assertThat(account.getPointsBalance()).isEqualTo(125);
    }

    /** Vé được điểm/voucher phủ hết (payable = 0) thì không tích thêm điểm. */
    @Test
    void earnFromTicket_zeroPayable_earnsNothing() {
        service.earnFromTicket(ticket(BigDecimal.ZERO, null));

        verify(transactionRepository, never()).save(any());
        assertThat(account.getPointsBalance()).isEqualTo(100);
    }

    /** Không ném lỗi vào luồng chính: tích điểm hỏng không được làm vỡ thanh toán. */
    @Test
    void earnFromTicket_swallowsErrors() {
        when(accountRepository.lockById(1L)).thenThrow(new IllegalStateException("db down"));

        service.earnFromTicket(ticket(BigDecimal.valueOf(250_000), null));
    }

    @Test
    void consumeForTicket_debitsExactPointsUsed() {
        service.consumeForTicket(ticket(BigDecimal.valueOf(700_000), 30));

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LoyaltyTxnType.REDEEM);
        assertThat(captor.getValue().getPoints()).isEqualTo(-30);
        assertThat(account.getPointsBalance()).isEqualTo(70);
    }

    @Test
    void consumeForTicket_noPointsUsed_isNoop() {
        service.consumeForTicket(ticket(BigDecimal.valueOf(700_000), null));

        verify(transactionRepository, never()).save(any());
    }

    /** Số dư không đủ phải CHẶN — đây là đường tiêu tiền thật, không nuốt lỗi. */
    @Test
    void consumeForTicket_insufficientBalance_isRejected() {
        assertThatThrownBy(() -> service.consumeForTicket(ticket(BigDecimal.valueOf(700_000), 500)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough points");
        assertThat(account.getPointsBalance()).isEqualTo(100);
    }

    @Test
    void refundToTicket_givesPointsBack() {
        service.refundToTicket(ticket(BigDecimal.valueOf(700_000), 30));

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LoyaltyTxnType.REFUND);
        assertThat(captor.getValue().getPoints()).isEqualTo(30);
        assertThat(account.getPointsBalance()).isEqualTo(130);
    }

    @Test
    void refundToTicket_noPointsUsed_isNoop() {
        service.refundToTicket(ticket(BigDecimal.valueOf(700_000), null));

        verify(transactionRepository, never()).save(any());
    }
}
