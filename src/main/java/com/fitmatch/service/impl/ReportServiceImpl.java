package com.fitmatch.service.impl;

import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.dto.report.OperationalReportResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.ReportService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TicketRepository ticketRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final DisputeRepository disputeRepository;
    private final WalletRepository walletRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional(readOnly = true)
    public OperationalReportResponse platformReport(LocalDate from, LocalDate to) {
        return build(from, to, null, "PLATFORM", null);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationalReportResponse gymReport(String gymUsername, LocalDate from, LocalDate to) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        Wallet wallet = walletRepository.findByGymProfile_Id(gym.getId()).orElse(null);
        return build(from, to, gym.getId(), "GYM", wallet);
    }

    private OperationalReportResponse build(LocalDate from, LocalDate to, Long gymId,
                                            String scope, Wallet wallet) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay(); // bao trọn ngày 'to'

        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        long totalBookings = 0;
        // Mô hình vé: "booking" trong báo cáo giờ là VÉ ĐÃ BÁN. Giữ tên trường
        // của response để dashboard cũ không vỡ.
        for (Object[] row : ticketRepository.countByStatusInRange(start, end, gymId)) {
            String status = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            bookingsByStatus.put(status, count);
            totalBookings += count;
        }

        Map<WalletTxnType, BigDecimal> byType = new java.util.EnumMap<>(WalletTxnType.class);
        for (Object[] row : walletTransactionRepository.sumByTypeInRange(start, end, gymId)) {
            byType.put((WalletTxnType) row[0], (BigDecimal) row[1]);
        }

        Map<String, Long> disputesByStatus = new LinkedHashMap<>();
        long totalDisputes = 0;
        for (Object[] row : disputeRepository.countByStatusInRange(start, end, gymId)) {
            String status = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            disputesByStatus.put(status, count);
            totalDisputes += count;
        }

        var builder = OperationalReportResponse.builder()
                .from(from).to(to).scope(scope)
                .totalBookings(totalBookings)
                .bookingsByStatus(bookingsByStatus)
                .grossHeld(byType.getOrDefault(WalletTxnType.HOLD, BigDecimal.ZERO))
                .refunded(byType.getOrDefault(WalletTxnType.REFUND, BigDecimal.ZERO))
                .releasedNet(byType.getOrDefault(WalletTxnType.RELEASE, BigDecimal.ZERO))
                .commission(byType.getOrDefault(WalletTxnType.COMMISSION, BigDecimal.ZERO))
                .totalDisputes(totalDisputes)
                .disputesByStatus(disputesByStatus);

        if (wallet != null) {
            builder.walletHeld(wallet.getHeldBalance())
                    .walletPending(wallet.getPendingBalance())
                    .walletAvailable(wallet.getAvailableBalance())
                    .walletFrozen(wallet.getFrozenBalance());
        }
        return builder.build();
    }
}
