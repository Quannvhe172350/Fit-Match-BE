package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TicketTypeBranch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeResponse {

    private Long id;
    private String name;
    private String description;
    private TicketKind kind;
    private Integer dayCount;
    private BigDecimal price;
    private BigDecimal ptSurchargePerDay;

    /**
     * Giá trọn gói khi chọn PT = price + ptSurchargePerDay * dayCount. Tính sẵn ở
     * BE để FE không phải nhân lại — hai nơi nhân là hai nơi có thể lệch.
     */
    private BigDecimal priceWithPt;

    private CatalogStatus status;
    private boolean active;

    /** Chi nhánh đang bán loại vé này. */
    private List<BranchRef> branches;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchRef {
        private Long id;
        private String name;
    }

    public static TicketTypeResponse of(TicketType t) {
        BigDecimal surcharge = t.getPtSurchargePerDay() != null
                ? t.getPtSurchargePerDay() : BigDecimal.ZERO;
        return TicketTypeResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .description(t.getDescription())
                .kind(t.getKind())
                .dayCount(t.getDayCount())
                .price(t.getPrice())
                .ptSurchargePerDay(t.getPtSurchargePerDay())
                .priceWithPt(t.getPrice().add(surcharge.multiply(BigDecimal.valueOf(t.getDayCount()))))
                .status(t.getStatus())
                .active(t.isActive())
                .branches(t.getBranches().stream().map(TicketTypeResponse::branchRef).toList())
                .build();
    }

    private static BranchRef branchRef(TicketTypeBranch link) {
        return BranchRef.builder()
                .id(link.getGymBranch().getId())
                .name(link.getGymBranch().getName())
                .build();
    }
}
