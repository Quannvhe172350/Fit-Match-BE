package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TicketTypeBranch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một loại vé đang bán, kèm phòng gym bán nó — dùng cho trang duyệt vé công khai.
 *
 * <p>Khác {@link TicketTypeResponse} (dành cho gym tự quản lý catalog của mình,
 * nên không cần nhắc lại gym là ai) ở chỗ item ở đây đứng một mình trong danh
 * sách toàn sàn: không mang tên gym và chi nhánh thì người xem không biết mua ở
 * đâu, và FE không dựng được liên kết tới {@code /checkout}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceTicketTypeResponse {

    private Long id;
    private String name;
    private String description;
    private TicketKind kind;
    private Integer dayCount;
    private BigDecimal price;
    private BigDecimal ptSurchargePerDay;

    /** Giá trọn gói khi có PT = price + ptSurchargePerDay * dayCount. */
    private BigDecimal priceWithPt;

    private Long gymId;
    private String gymName;
    private String gymCity;
    private String gymDistrict;

    /**
     * Các chi nhánh bán loại vé này. Mua vé BẮT BUỘC có branchId nên danh sách
     * này là thứ FE cần để dựng liên kết checkout; rỗng nghĩa là không mua được.
     */
    private List<BranchRef> branches;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchRef {
        private Long id;
        private String name;
    }

    public static MarketplaceTicketTypeResponse of(TicketType t) {
        BigDecimal surcharge = t.getPtSurchargePerDay() != null
                ? t.getPtSurchargePerDay() : BigDecimal.ZERO;
        int days = t.getDayCount() != null ? t.getDayCount() : 1;

        return MarketplaceTicketTypeResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .description(t.getDescription())
                .kind(t.getKind())
                .dayCount(t.getDayCount())
                .price(t.getPrice())
                .ptSurchargePerDay(t.getPtSurchargePerDay())
                .priceWithPt(t.getPrice().add(surcharge.multiply(BigDecimal.valueOf(days))))
                .gymId(t.getGymProfile().getId())
                .gymName(t.getGymProfile().getGymName())
                .gymCity(t.getGymProfile().getCity())
                .gymDistrict(t.getGymProfile().getDistrict())
                .branches(t.getBranches().stream()
                        .map(MarketplaceTicketTypeResponse::branchRef)
                        .toList())
                .build();
    }

    private static BranchRef branchRef(TicketTypeBranch link) {
        return BranchRef.builder()
                .id(link.getGymBranch().getId())
                .name(link.getGymBranch().getName())
                .build();
    }
}
