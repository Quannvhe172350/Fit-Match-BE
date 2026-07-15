package com.fitmatch.dto.loyalty;

import com.fitmatch.common.response.PageResponse;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Số dư điểm + quy đổi + lịch sử (UC-073). */
@Getter
@Builder
public class LoyaltyBalanceResponse {

    private int pointsBalance;
    /** Giá trị 1 điểm khi quy đổi (VND). */
    private BigDecimal pointValue;
    /** Số VND cần chi để được 1 điểm. */
    private BigDecimal vndPerPoint;
    private PageResponse<LoyaltyTransactionResponse> history;
}
