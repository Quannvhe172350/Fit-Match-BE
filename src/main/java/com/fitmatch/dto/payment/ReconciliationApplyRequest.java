package com.fitmatch.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UC-053/056: gắn tay một giao dịch đã vào tài khoản vào booking đang chờ trả
 * tiền (ca phổ biến: khách chuyển khoản sai nội dung nên không khớp refCode).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationApplyRequest {

    @NotNull(message = "Booking id is required")
    private Long bookingId;

    /**
     * Cho phép áp dù số tiền giao dịch nhỏ hơn số phải trả — dùng cho ca khách
     * chuyển khoản nhiều lần (mỗi lần thiếu, tổng thì đủ): Finance đối chiếu sao
     * kê rồi tự chịu trách nhiệm. Bắt buộc kèm {@code note} giải trình.
     */
    @Builder.Default
    private boolean allowAmountMismatch = false;

    @Size(max = 500)
    private String note;
}
