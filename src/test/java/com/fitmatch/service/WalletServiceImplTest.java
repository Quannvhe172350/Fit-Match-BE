package com.fitmatch.service;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.entity.User;
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
    @Mock private com.fitmatch.service.support.WalletCreator walletCreator;
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

    // ----- V61: ví đa chủ sở hữu -----

    /** Ví khách hàng để kiểm thử luồng hoàn tiền chạm hai ví. */
    private Wallet secondWallet(long id, WalletOwnerType ownerType) {
        Wallet w = Wallet.builder().id(id).ownerType(ownerType)
                .heldBalance(BigDecimal.ZERO).pendingBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO).frozenBalance(BigDecimal.ZERO)
                .build();
        lenient().when(walletRepository.lockById(id)).thenReturn(Optional.of(w));
        return w;
    }

    @Test
    void refundToCustomer_debitsGymHeldAndCreditsCustomerAvailable() {
        wallet.setOwnerType(WalletOwnerType.GYM);
        wallet.setHeldBalance(new BigDecimal("300.00"));
        Wallet customerWallet = secondWallet(2L, WalletOwnerType.CUSTOMER);
        User customer = User.builder().id(42L).username("khach").build();
        when(walletRepository.findByUser_Id(42L)).thenReturn(Optional.of(customerWallet));

        service.refundToCustomer(5L, customer, 10L, new BigDecimal("120.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("180.00");
        assertThat(customerWallet.getAvailableBalance()).isEqualByComparingTo("120.00");

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(WalletTransaction::getType)
                .containsExactly(WalletTxnType.REFUND, WalletTxnType.REFUND_CREDIT);
    }

    @Test
    void refundToCustomer_withoutCustomer_fallsBackToHeldDebitOnly() {
        // Dữ liệu cũ không truy ra được khách: vẫn phải hoàn được, chỉ là không
        // ghi có vào ví nào — chặn hẳn luồng refund còn tệ hơn.
        wallet.setOwnerType(WalletOwnerType.GYM);
        wallet.setHeldBalance(new BigDecimal("300.00"));

        service.refundToCustomer(5L, null, 10L, new BigDecimal("120.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("180.00");
        verify(walletTransactionRepository, org.mockito.Mockito.times(1)).save(any(WalletTransaction.class));
    }

    // ----- Tạo ví lazy dưới truy cập đồng thời -----

    @Test
    void getOrCreateForCustomer_losesCreationRace_reusesWalletFromWinner() {
        // FE mở màn hình ví bắn nhiều request song song; tất cả cùng thấy ví
        // chưa có rồi cùng INSERT. Request thua UK_wallets_user phải nhận ví của
        // request thắng, không được ném lỗi ra người dùng.
        User customer = User.builder().id(42L).username("khach").build();
        Wallet created = secondWallet(2L, WalletOwnerType.CUSTOMER);
        when(walletRepository.findByUser_Id(42L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("UK_wallets_user"))
                .when(walletCreator).createForCustomer(customer);
        when(walletRepository.lockByUserId(42L)).thenReturn(Optional.of(created));

        assertThat(service.getOrCreateForCustomer(customer)).isSameAs(created);
    }

    @Test
    void getOrCreateForCustomer_createsWallet_returnsManagedInstanceFromReread() {
        // Kể cả khi tạo thành công vẫn phải đọc lại: bản ghi do transaction bên
        // trong tạo ra là detached, trả thẳng ra thì đổi số dư sẽ không xuống DB.
        User customer = User.builder().id(42L).username("khach").build();
        Wallet created = secondWallet(2L, WalletOwnerType.CUSTOMER);
        when(walletRepository.findByUser_Id(42L)).thenReturn(Optional.empty());
        when(walletRepository.lockByUserId(42L)).thenReturn(Optional.of(created));

        assertThat(service.getOrCreateForCustomer(customer)).isSameAs(created);
        verify(walletCreator).createForCustomer(customer);
    }

    @Test
    void getOrCreate_losesCreationRace_reusesWalletFromWinner() {
        com.fitmatch.entity.GymProfile gym = com.fitmatch.entity.GymProfile.builder().id(7L).build();
        Wallet created = secondWallet(3L, WalletOwnerType.GYM);
        when(walletRepository.findByGymProfile_Id(7L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("gym_profile_id"))
                .when(walletCreator).createForGym(gym);
        when(walletRepository.lockByGymProfileId(7L)).thenReturn(Optional.of(created));

        assertThat(service.getOrCreate(gym)).isSameAs(created);
    }

    @Test
    void getOrCreate_integrityErrorFromAnotherCause_rethrows() {
        // Đọc lại vẫn rỗng -> lỗi không đến từ cuộc đua (vd chk_wallet_single_owner);
        // che đi sẽ biến lỗi dữ liệu thật thành "không tìm thấy ví".
        com.fitmatch.entity.GymProfile gym = com.fitmatch.entity.GymProfile.builder().id(7L).build();
        when(walletRepository.findByGymProfile_Id(7L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("chk_wallet_single_owner"))
                .when(walletCreator).createForGym(gym);
        when(walletRepository.lockByGymProfileId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate(gym))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("chk_wallet_single_owner");
    }
}
