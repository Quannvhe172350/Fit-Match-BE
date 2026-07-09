package com.fitmatch.dto.admin;

import com.fitmatch.entity.CommissionConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionConfigResponse {

    private Long id;
    private BigDecimal commissionPercent;
    private BigDecimal platformFeePercent;
    private Integer settlementHoldDays;

    public static CommissionConfigResponse of(CommissionConfig c) {
        return CommissionConfigResponse.builder()
                .id(c.getId())
                .commissionPercent(c.getCommissionPercent())
                .platformFeePercent(c.getPlatformFeePercent())
                .settlementHoldDays(c.getSettlementHoldDays())
                .build();
    }
}
