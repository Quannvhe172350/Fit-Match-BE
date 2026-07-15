package com.fitmatch.dto.voucher;

import com.fitmatch.common.enums.DiscountType;
import com.fitmatch.entity.Voucher;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Voucher (UC-073). */
@Getter
@Builder
public class VoucherResponse {

    private Long id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minBookingAmount;
    private BigDecimal maxDiscount;
    private Integer usageLimit;
    private int usedCount;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private boolean active;

    public static VoucherResponse of(Voucher v) {
        return VoucherResponse.builder()
                .id(v.getId())
                .code(v.getCode())
                .description(v.getDescription())
                .discountType(v.getDiscountType())
                .discountValue(v.getDiscountValue())
                .minBookingAmount(v.getMinBookingAmount())
                .maxDiscount(v.getMaxDiscount())
                .usageLimit(v.getUsageLimit())
                .usedCount(v.getUsedCount())
                .validFrom(v.getValidFrom())
                .validTo(v.getValidTo())
                .active(v.isActive())
                .build();
    }
}
