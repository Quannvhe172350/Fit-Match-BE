package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPolicyResponse {

    private Long id;
    private String bookingPolicy;
    private String cancellationPolicy;
    private String noShowPolicy;
    private String houseRules;

    /** V94 — mốc hoàn tiền khi khách huỷ ngày tập. Xem GymPolicyRequest. */
    private Integer cancelFullRefundHours;
    private Integer cancelPartialRefundHours;
    private java.math.BigDecimal cancelPartialRefundPercent;

    public static GymPolicyResponse of(GymPolicy p) {
        return GymPolicyResponse.builder()
                .id(p.getId())
                .bookingPolicy(p.getBookingPolicy())
                .cancellationPolicy(p.getCancellationPolicy())
                .noShowPolicy(p.getNoShowPolicy())
                .houseRules(p.getHouseRules())
                .cancelFullRefundHours(p.getCancelFullRefundHours())
                .cancelPartialRefundHours(p.getCancelPartialRefundHours())
                .cancelPartialRefundPercent(p.getCancelPartialRefundPercent())
                .build();
    }
}
