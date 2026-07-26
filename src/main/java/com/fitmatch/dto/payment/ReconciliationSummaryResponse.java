package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.PaymentTxnAnomaly;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** Tổng quan hàng đợi đối soát — tiền đang "treo" chưa gắn được vào booking (UC-053). */
@Getter
@Builder
public class ReconciliationSummaryResponse {

    /** Tổng số giao dịch còn chờ Finance xử lý. */
    private long needsReviewCount;

    /** Tổng số tiền của các giao dịch đó (gồm cả phần đã áp vào booking ở ca OVERPAID). */
    private BigDecimal needsReviewAmount;

    private List<AnomalyBucket> byAnomaly;

    @Getter
    @Builder
    public static class AnomalyBucket {
        private PaymentTxnAnomaly anomaly;
        private long count;
        private BigDecimal amount;
    }
}
