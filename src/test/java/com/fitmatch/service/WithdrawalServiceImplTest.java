package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WithdrawalRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.WithdrawalRequestRepository;
import com.fitmatch.service.impl.WithdrawalServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
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
    @Mock private WalletService walletService;
    @Mock private GymProfileResolver gymProfileResolver;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private WithdrawalServiceImpl service;

    private final GymProfile gym = GymProfile.builder()
            .id(5L)
            .user(com.fitmatch.entity.User.builder().id(9L).username("gym").build())
            .build();
    private final Wallet wallet = Wallet.builder().id(7L).gymProfile(gym).build();

    private WithdrawalRequest request(WithdrawalStatus status) {
        return WithdrawalRequest.builder()
                .id(1L).wallet(wallet).amount(new BigDecimal("500.00"))
                .bankAccount("0123456789").bankName("VCB").accountHolder("GYM A")
                .status(status)
                .build();
    }

    @Test
    void create_reservesAvailableBalance() {
        when(gymProfileResolver.requireApprovedGym("gym")).thenReturn(gym);
        when(walletService.getOrCreate(gym)).thenReturn(wallet);
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WithdrawalResponse res = service.create("gym", new WithdrawalCreateRequest(
                new BigDecimal("500.00"), "0123456789", "VCB", "GYM A"));

        verify(walletService).reserveForWithdrawal(5L, new BigDecimal("500.00"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
    }

    @Test
    void reject_returnsReserveToAvailable() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.PENDING)));

        WithdrawalResponse res = service.reject(1L, "invalid bank info", "finance");

        verify(walletService).cancelWithdrawalReserve(5L, new BigDecimal("500.00"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
    }

    @Test
    void markPaid_fromApproved_paysOut() {
        when(withdrawalRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(WithdrawalStatus.APPROVED)));

        WithdrawalResponse res = service.markPaid(1L, "FT2026071799", "txn 998877", "finance");

        verify(walletService).payoutWithdrawal(5L, new BigDecimal("500.00"));
        assertThat(res.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(res.getPayoutReference()).isEqualTo("FT2026071799");
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
}
