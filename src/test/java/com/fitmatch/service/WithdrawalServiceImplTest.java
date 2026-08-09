package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.entity.Bank;
import com.fitmatch.entity.BankAccount;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WithdrawalRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BankAccountRepository;
import com.fitmatch.repository.WithdrawalRequestRepository;
import com.fitmatch.service.impl.WithdrawalServiceImpl;
import com.fitmatch.service.support.PayoutQrService;
import com.fitmatch.service.support.WalletOwnerResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private WalletService walletService;
    @Mock private WalletOwnerResolver walletOwnerResolver;
    @Mock private PayoutQrService payoutQrService;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private WithdrawalServiceImpl service;

    private final GymProfile gym = GymProfile.builder()
            .id(5L)
            .gymName("Gym A")
            .user(com.fitmatch.entity.User.builder().id(9L).username("gym").build())
            .build();
    private final Wallet wallet = Wallet.builder()
            .id(7L).ownerType(WalletOwnerType.GYM).gymProfile(gym).build();

    private final Bank vcb = Bank.builder()
            .id(1L).bin("970436").code("VCB").shortName("Vietcombank").name("Vietcombank").build();
    private final BankAccount account = BankAccount.builder()
            .id(3L).bank(vcb).accountNumber("0123456789").accountHolder("GYM A").build();

    private WithdrawalRequest request(WithdrawalStatus status) {
        return WithdrawalRequest.builder()
                .id(1L).wallet(wallet).amount(new BigDecimal("500"))
                .refCode("FMW1ABC123")
                .bankAccount("0123456789").bankName("Vietcombank").bankBin("970436")
                .accountHolder("GYM A")
                .status(status)
                .build();
    }

    @Test
    void create_reservesAvailableBalanceAndSnapshotsBankDetails() {
        when(walletOwnerResolver.resolve("gym", WalletOwnerType.GYM)).thenReturn(wallet);
        when(bankAccountRepository.findByIdAndUser_Username(3L, "gym")).thenReturn(Optional.of(account));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class)))
                .thenAnswer(inv -> {
                    WithdrawalRequest r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(11L);
                    }
                    return r;
                });

        WithdrawalResponse res = service.create("gym", WalletOwnerType.GYM,
                new WithdrawalCreateRequest(new BigDecimal("500"), 3L));

        verify(walletService).reserveForWithdrawal(wallet, new BigDecimal("500"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
        // Mã BIN phải được chốt vào lệnh, nếu không thì bước duyệt không dựng được QR.
        assertThat(res.getBankBin()).isEqualTo("970436");
        assertThat(res.getBankAccount()).isEqualTo("0123456789");
        assertThat(res.getRefCode()).startsWith("FMW11");
    }

    @Test
    void create_withFractionalAmount_throwsValidation() {
        // Số lẻ đồng thì QR (chỉ nhận số nguyên) và số đã giữ chỗ sẽ lệch nhau,
        // giao dịch chi trên sao kê không bao giờ khớp -> lệnh kẹt ở APPROVED.
        assertThatThrownBy(() -> service.create("gym", WalletOwnerType.GYM,
                new WithdrawalCreateRequest(new BigDecimal("500.50"), 3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void approve_generatesPayoutQr() {
        WithdrawalRequest pending = request(WithdrawalStatus.PENDING);
        when(withdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(payoutQrService.buildQrUrl(pending)).thenReturn("https://img.vietqr.io/image/x.png");

        WithdrawalResponse res = service.approve(1L, null, "finance");

        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(res.getQrContent()).isEqualTo("https://img.vietqr.io/image/x.png");
    }

    @Test
    void reject_returnsReserveToAvailable() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.PENDING)));

        WithdrawalResponse res = service.reject(1L, "invalid bank info", "finance");

        verify(walletService).cancelWithdrawalReserve(wallet, new BigDecimal("500"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
    }

    @Test
    void markPaid_fromApproved_paysOut() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.APPROVED)));

        WithdrawalResponse res = service.markPaid(1L, "FT2026071799", "txn 998877", "finance");

        verify(walletService).payoutWithdrawal(wallet, new BigDecimal("500"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(res.getPayoutReference()).isEqualTo("FT2026071799");
        assertThat(res.getPaidAt()).isNotNull();
        assertThat(res.isAutoMatched()).isFalse();
    }

    @Test
    void markPaidByReconciliation_marksAutoMatched() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.APPROVED)));

        WithdrawalResponse res = service.markPaidByReconciliation(1L, "casso-778");

        verify(walletService).payoutWithdrawal(wallet, new BigDecimal("500"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(res.getPayoutReference()).isEqualTo("CASSO-casso-778");
        assertThat(res.isAutoMatched()).isTrue();
    }

    @Test
    void markPaid_withoutPayoutReference_throwsValidation() {
        // D-11: không có mã giao dịch chuyển khoản thì không đối soát được sao kê.
        assertThatThrownBy(() -> service.markPaid(1L, "  ", null, "finance"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void markPaid_fromPending_throwsInvalidState() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.PENDING)));

        assertThatThrownBy(() -> service.markPaid(1L, "FT123", null, "finance"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void customerWithdrawal_notifiesCustomerWalletPage() {
        User customer = User.builder().id(12L).username("khach").fullName("Nguyễn A").build();
        Wallet customerWallet = Wallet.builder()
                .id(8L).ownerType(WalletOwnerType.CUSTOMER).user(customer).build();
        WithdrawalRequest r = request(WithdrawalStatus.PENDING);
        r.setWallet(customerWallet);
        when(withdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        WithdrawalResponse res = service.reject(1L, "sai số tài khoản", "finance");

        assertThat(res.getOwnerType()).isEqualTo(WalletOwnerType.CUSTOMER);
        assertThat(res.getOwnerName()).isEqualTo("Nguyễn A");
        verify(notificationDispatcher).withdrawalDecided(
                customer, 1L, "bị từ chối",
                "Lệnh rút 500 đ bị từ chối: sai số tài khoản. Số tiền đã trở lại khả dụng.",
                "/profile/wallet");
    }
}
