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
import java.util.List;
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
        lenient().when(walletRepository.lockByGymProfileId(5L)).thenReturn(Optional.of(wallet));
        lenient().when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
    }

    private WalletTransaction lastTxn() {
        return allTxns().get(allTxns().size() - 1);
    }

    private List<WalletTransaction> allTxns() {
        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getAllValues();
    }

    /**
     * Hồi quy: ví vừa được {@code WalletCreator} tạo ở transaction REQUIRES_NEW
     * thì transaction ngoài (REPEATABLE READ) KHÔNG thấy bằng truy vấn thường,
     * chỉ thấy bằng truy vấn có khoá. Bản cũ đọc thường trước rồi mới khoá nên
     * lần thanh toán đầu tiên của mỗi gym luôn ném "Wallet for gym not found",
     * vé kẹt PENDING_PAYMENT và tiền không được giữ.
     */
    @Test
    void holdForTicket_findsWalletVisibleOnlyToLockingRead() {
        // Mô phỏng đúng tình huống thật: truy vấn THƯỜNG không thấy ví (ảnh chụp
        // REPEATABLE READ cũ hơn lần commit tạo ví), chỉ truy vấn CÓ KHOÁ mới thấy.
        lenient().when(walletRepository.findByGymProfile_Id(5L)).thenReturn(Optional.empty());

        service.holdForTicket(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void holdForTicket_increasesHeldAndRecordsLedger() {
        service.holdForTicket(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
        assertThat(lastTxn().getType()).isEqualTo(WalletTxnType.HOLD);
        assertThat(lastTxn().getHeldAfter()).isEqualByComparingTo("200.00");
        // Neo vé là thứ duy nhất truy vết được bút toán về nguồn tiền (V73);
        // ghi thiếu thì sổ cái không đối soát ngược được.
        assertThat(lastTxn().getTicketId()).isEqualTo(10L);
    }

    @Test
    void refundForTicket_insufficientHeld_throwsAndNoChange() {
        wallet.setHeldBalance(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.refundToCustomerForTicket(
                5L, null, 10L, new BigDecimal("150.00")))
                .isInstanceOf(BusinessException.class);
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void moveToPendingForTicket_shiftsHeldToPending() {
        wallet.setHeldBalance(new BigDecimal("200.00"));

        service.moveToPendingForTicket(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void reverseToHeldForTicket_pullsPendingBackToHeld() {
        wallet.setPendingBalance(new BigDecimal("200.00"));

        service.reverseToHeldForTicket(5L, 10L, new BigDecimal("200.00"));

        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
        assertThat(lastTxn().getType()).isEqualTo(WalletTxnType.DISPUTE_HOLD);
    }

    @Test
    void releaseForTicket_movesNetToAvailableAfterCommission() {
        wallet.setPendingBalance(new BigDecimal("200.00"));

        service.releaseForTicket(5L, 10L, new BigDecimal("200.00"), new BigDecimal("15.00"));

        // commission 15% = 30 -> net 170 vào available, pending về 0.
        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("170.00");
        // ledger ghi cả RELEASE và COMMISSION.
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(any(WalletTransaction.class));
    }

    @Test
    void negativeAmount_rejected() {
        assertThatThrownBy(() -> service.holdForTicket(5L, 10L, new BigDecimal("-1")))
                .isInstanceOf(BusinessException.class);
    }

    // ----- Neo vé trong sổ cái (V73) -----
    //
    // Hai test dưới canh chỗ mà mắt thường KHÔNG thấy được. Trước đây record()
    // có hai overload: (…, Long bookingId, String desc) và (…, Long bookingId,
    // Long ticketId, String desc). Khi mô hình booking chết, hai overload gộp
    // làm một và tham số Long thứ nhất ĐỔI NGHĨA từ bookingId sang ticketId.
    //
    // Một lời gọi truyền nhầm vị trí vẫn biên dịch sạch, vẫn chạy, vẫn ra số dư
    // đúng — chỉ có neo truy vết là sai. Sổ cái ví là append-only nên sai kiểu
    // này KHÔNG sửa lại được, chỉ lộ ra lúc đối soát.

    @Test
    void everyTicketEscrowOperation_stampsTicketAnchor() {
        wallet.setOwnerType(WalletOwnerType.GYM);

        service.holdForTicket(5L, 77L, new BigDecimal("300.00"));
        service.moveToPendingForTicket(5L, 77L, new BigDecimal("100.00"));
        service.reverseToHeldForTicket(5L, 77L, new BigDecimal("50.00"));
        service.releaseForTicket(5L, 77L, new BigDecimal("50.00"), new BigDecimal("10.00"));
        service.refundToCustomerForTicket(5L, null, 77L, new BigDecimal("100.00"));

        // releaseForTicket ghi HAI bút toán (RELEASE + COMMISSION) nên tổng là 6.
        assertThat(allTxns()).hasSize(6);
        assertThat(allTxns())
                .as("mọi bút toán escrow vé phải neo được về đúng vé")
                .allSatisfy(t -> assertThat(t.getTicketId()).isEqualTo(77L));
        assertThat(allTxns()).extracting(WalletTransaction::getType)
                .containsExactly(WalletTxnType.HOLD, WalletTxnType.MOVE_TO_PENDING,
                        WalletTxnType.DISPUTE_HOLD, WalletTxnType.RELEASE,
                        WalletTxnType.COMMISSION, WalletTxnType.REFUND);
    }

    @Test
    void nonTicketOperations_leaveTicketAnchorNull() {
        // Đóng băng và rút tiền KHÔNG gắn với vé nào. Nếu một trong số này lỡ
        // ghi số vào ticket_id, báo cáo đối soát sẽ quy khoản rút của gym thành
        // dòng tiền của một vé có thật — sai lệch mà số dư vẫn khớp.
        wallet.setOwnerType(WalletOwnerType.GYM);
        wallet.setAvailableBalance(new BigDecimal("500.00"));

        service.freezeWallet(wallet, new BigDecimal("100.00"), "điều tra");
        service.unfreezeWallet(wallet, new BigDecimal("100.00"), "gỡ điều tra");
        service.reserveForWithdrawal(wallet, new BigDecimal("200.00"));
        service.payoutWithdrawal(wallet, new BigDecimal("200.00"));
        service.reserveForWithdrawal(wallet, new BigDecimal("150.00"));
        service.cancelWithdrawalReserve(wallet, new BigDecimal("150.00"));

        assertThat(allTxns()).hasSize(6);
        assertThat(allTxns())
                .as("bút toán không thuộc vé nào phải để trống neo vé")
                .allSatisfy(t -> assertThat(t.getTicketId()).isNull());
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
    void refundToCustomerForTicket_debitsGymHeldAndCreditsCustomerAvailable() {
        wallet.setOwnerType(WalletOwnerType.GYM);
        wallet.setHeldBalance(new BigDecimal("300.00"));
        Wallet customerWallet = secondWallet(2L, WalletOwnerType.CUSTOMER);
        User customer = User.builder().id(42L).username("khach").build();
        when(walletRepository.findByUser_Id(42L)).thenReturn(Optional.of(customerWallet));

        service.refundToCustomerForTicket(5L, customer, 10L, new BigDecimal("120.00"));

        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("180.00");
        assertThat(customerWallet.getAvailableBalance()).isEqualByComparingTo("120.00");

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(WalletTransaction::getType)
                .containsExactly(WalletTxnType.REFUND, WalletTxnType.REFUND_CREDIT);
    }

    @Test
    void refundToCustomerForTicket_withoutCustomer_fallsBackToHeldDebitOnly() {
        // Không truy ra được khách: vẫn phải hoàn được, chỉ là không ghi có vào
        // ví nào — chặn hẳn luồng refund còn tệ hơn.
        wallet.setOwnerType(WalletOwnerType.GYM);
        wallet.setHeldBalance(new BigDecimal("300.00"));

        service.refundToCustomerForTicket(5L, null, 10L, new BigDecimal("120.00"));

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
