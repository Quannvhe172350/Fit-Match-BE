package com.fitmatch.service;

import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WalletTransaction;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-25: kiểm thử lõi sổ cái ví (giữ/hoàn/pending/giải ngân/kéo về held) —
 * bất biến số dư và bút toán ledger. Trước đây module tiền này không có test.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @InjectMocks private WalletServiceImpl service;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.builder().id(1L)
                .heldBalance(BigDecimal.ZERO).pendingBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO).frozenBalance(BigDecimal.ZERO)
                .build();
        lenient().when(walletRepository.findByGymProfile_Id(5L)).thenReturn(Optional.of(wallet));
        lenient().when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
    }

    private WalletTransaction lastTxn() {
        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void hold_increasesHeldAndRecordsLedger() {
        service.hold(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
        assertThat(lastTxn().getType()).isEqualTo(WalletTxnType.HOLD);
        assertThat(lastTxn().getHeldAfter()).isEqualByComparingTo("200.00");
    }

    @Test
    void refundFromHeld_decreasesHeld() {
        wallet.setHeldBalance(new BigDecimal("200.00"));

        service.refundFromHeld(5L, 10L, new BigDecimal("150.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("50.00");
        assertThat(lastTxn().getType()).isEqualTo(WalletTxnType.REFUND);
    }

    @Test
    void refundFromHeld_insufficientHeld_throwsAndNoChange() {
        wallet.setHeldBalance(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.refundFromHeld(5L, 10L, new BigDecimal("150.00")))
                .isInstanceOf(BusinessException.class);
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void moveToPending_shiftsHeldToPending() {
        wallet.setHeldBalance(new BigDecimal("200.00"));

        service.moveToPending(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void reverseToHeld_pullsPendingBackToHeld() {
        wallet.setPendingBalance(new BigDecimal("200.00"));

        service.reverseToHeld(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
        assertThat(lastTxn().getType()).isEqualTo(WalletTxnType.DISPUTE_HOLD);
    }

    @Test
    void release_movesNetToAvailableAfterCommission() {
        wallet.setPendingBalance(new BigDecimal("200.00"));

        service.release(5L, 10L, new BigDecimal("200.00"), new BigDecimal("15.00"));

        // commission 15% = 30 -> net 170 vào available, pending về 0.
        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("170.00");
        // ledger ghi cả RELEASE và COMMISSION.
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(any(WalletTransaction.class));
    }

    @Test
    void negativeAmount_rejected() {
        assertThatThrownBy(() -> service.hold(5L, 10L, new BigDecimal("-1")))
                .isInstanceOf(BusinessException.class);
    }
}
