package com.fitmatch.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload webhook Casso (UC-053): danh sách biến động số dư ngân hàng.
 * Định dạng Casso: {"error":0,"data":[{id, amount, description, ...}]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CassoWebhookRequest {

    private Integer error;
    private List<Item> data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** Id giao dịch Casso — khoá idempotency. */
        private String id;
        private BigDecimal amount;
        /** Nội dung chuyển khoản; chứa refCode của đơn thanh toán. */
        private String description;
    }
}
