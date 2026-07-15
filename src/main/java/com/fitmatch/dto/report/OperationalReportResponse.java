package com.fitmatch.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Báo cáo vận hành & tài chính (UC-076). Phạm vi: toàn nền tảng (Admin/Finance)
 * hoặc một Gym (Gym Operator). Số liệu tài chính lấy từ sổ cái ví (nguồn sự thật).
 */
@Getter
@Builder
public class OperationalReportResponse {

    private LocalDate from;
    private LocalDate to;
    private String scope; // "PLATFORM" | "GYM"

    // ----- Booking (UC-076) -----
    private long totalBookings;
    private Map<String, Long> bookingsByStatus;

    // ----- Tài chính (từ WalletTransaction) -----
    /** Tổng tiền đã giữ (khách thanh toán) trong kỳ. */
    private BigDecimal grossHeld;
    /** Tổng đã hoàn cho khách. */
    private BigDecimal refunded;
    /** Tổng ròng đã giải ngân về Gym (đã trừ hoa hồng). */
    private BigDecimal releasedNet;
    /** Tổng hoa hồng nền tảng thu được. */
    private BigDecimal commission;

    // ----- Số dư ví hiện tại (chỉ có ở báo cáo Gym) -----
    private BigDecimal walletHeld;
    private BigDecimal walletPending;
    private BigDecimal walletAvailable;
    private BigDecimal walletFrozen;

    // ----- Tranh chấp -----
    private long totalDisputes;
    private Map<String, Long> disputesByStatus;
}
