package com.fitmatch.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Dùng chung cho /tickets/quote và /tickets/purchase — hai đường phải nhận cùng
 * một input để số tiền hiển thị trước khi bấm mua không bao giờ lệch số tiền
 * thật sự bị trừ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQuoteRequest {

    @NotNull(message = "branchId is required")
    private Long branchId;

    @NotNull(message = "ticketTypeId is required")
    private Long ticketTypeId;

    /** Có chọn PT hay không — quyết định việc cộng phụ phí theo ngày (câu 6). */
    private boolean withPt;

    /** Mã voucher; null/rỗng = không áp. */
    private String voucherCode;

    /** Câu 14: bật là tiêu TOÀN BỘ điểm khả dụng, cap ở số tiền còn phải trả. */
    private boolean useLoyaltyPoints;

    /**
     * V82: dịch vụ kèm vé khách tick thêm. Null/rỗng = vé thuần.
     *
     * <p>Phải gửi ở CẢ /quote lẫn /purchase: FE không được tự cộng tiền dịch vụ,
     * vì payableAmount do BE chốt chính là số tiền in lên VietQR — lệch một đồng
     * là webhook luôn thấy "trả thiếu" và vé không bao giờ được kích hoạt.
     */
    private List<Long> serviceIds;
}
