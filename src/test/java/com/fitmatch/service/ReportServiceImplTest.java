package com.fitmatch.service;

import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.dto.report.OperationalReportResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.impl.ReportServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @InjectMocks private ReportServiceImpl service;

    @Test
    void platformReport_aggregatesAcrossPlatform() {
        when(ticketRepository.countByStatusInRange(any(), any(), isNull()))
                .thenReturn(List.of(
                        new Object[]{com.fitmatch.common.enums.TicketStatus.USED_UP, 5L},
                        new Object[]{com.fitmatch.common.enums.TicketStatus.CANCELLED, 2L}));
        when(walletTransactionRepository.sumByTypeInRange(any(), any(), isNull()))
                .thenReturn(List.of(
                        new Object[]{WalletTxnType.HOLD, new BigDecimal("1000.00")},
                        new Object[]{WalletTxnType.RELEASE, new BigDecimal("680.00")},
                        new Object[]{WalletTxnType.COMMISSION, new BigDecimal("120.00")},
                        new Object[]{WalletTxnType.REFUND, new BigDecimal("200.00")}));
        when(disputeRepository.countByStatusInRange(any(), any(), isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{com.fitmatch.common.enums.DisputeStatus.RESOLVED, 1L}));

        OperationalReportResponse r = service.platformReport(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(r.getScope()).isEqualTo("PLATFORM");
        assertThat(r.getTotalBookings()).isEqualTo(7);
        assertThat(r.getBookingsByStatus()).containsEntry("USED_UP", 5L);
        assertThat(r.getGrossHeld()).isEqualByComparingTo("1000.00");
        assertThat(r.getReleasedNet()).isEqualByComparingTo("680.00");
        assertThat(r.getCommission()).isEqualByComparingTo("120.00");
        assertThat(r.getRefunded()).isEqualByComparingTo("200.00");
        assertThat(r.getTotalDisputes()).isEqualTo(1);
        assertThat(r.getWalletAvailable()).isNull(); // platform report không kèm ví
    }

    @Test
    void gymReport_scopedToGym_includesWalletBalances() {
        GymProfile gym = GymProfile.builder().id(5L).build();
        when(gymProfileResolver.requireApprovedGym("gym")).thenReturn(gym);
        when(walletRepository.findByGymProfile_Id(5L)).thenReturn(Optional.of(
                Wallet.builder().id(7L).gymProfile(gym)
                        .heldBalance(new BigDecimal("300.00"))
                        .pendingBalance(new BigDecimal("100.00"))
                        .availableBalance(new BigDecimal("500.00"))
                        .frozenBalance(BigDecimal.ZERO).build()));
        when(ticketRepository.countByStatusInRange(any(), any(), eq(5L))).thenReturn(List.of());
        when(walletTransactionRepository.sumByTypeInRange(any(), any(), eq(5L))).thenReturn(List.of());
        when(disputeRepository.countByStatusInRange(any(), any(), eq(5L))).thenReturn(List.of());

        OperationalReportResponse r = service.gymReport("gym",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(r.getScope()).isEqualTo("GYM");
        assertThat(r.getWalletAvailable()).isEqualByComparingTo("500.00");
        assertThat(r.getGrossHeld()).isEqualByComparingTo("0"); // không có txn trong kỳ
    }
}
