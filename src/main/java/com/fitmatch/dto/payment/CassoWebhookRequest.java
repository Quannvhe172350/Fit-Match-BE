package com.fitmatch.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Payload webhook Casso (UC-053): danh sách biến động số dư ngân hàng.
 * <p>
 * Casso có 2 phiên bản webhook với cấu trúc data khác nhau:
 * <ul>
 *   <li><b>V1 (Original)</b>: {@code "data": [{...}]} — mảng transaction,
 *       xác thực bằng header {@code Secure-Token}.</li>
 *   <li><b>V2 (Khuyến nghị)</b>: {@code "data": {...}} — object đơn,
 *       xác thực bằng header {@code X-Casso-Signature} (HMAC-SHA256).</li>
 * </ul>
 * Deserializer tuỳ chỉnh {@link SingleOrListDeserializer} xử lý cả hai định dạng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CassoWebhookRequest {

    private Integer error;

    @JsonDeserialize(using = SingleOrListDeserializer.class)
    private List<Item> data;

    /**
     * Deserializer chấp nhận cả JSON array {@code [{...}]} (V1) và
     * JSON object đơn {@code {...}} (V2), luôn trả về {@code List<Item>}.
     */
    public static class SingleOrListDeserializer extends JsonDeserializer<List<Item>> {
        @Override
        public List<Item> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.isExpectedStartArrayToken()) {
                return p.readValueAs(new TypeReference<List<Item>>() {});
            }
            Item single = p.readValueAs(Item.class);
            return single != null ? Collections.singletonList(single) : Collections.emptyList();
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** Id giao dịch Casso — khoá idempotency (String để an toàn với mọi kiểu). */
        private String id;

        /** Số tiền giao dịch (VND). */
        private BigDecimal amount;

        /** Nội dung chuyển khoản; chứa refCode của đơn thanh toán. */
        private String description;

        // ---------- V2 fields (tham khảo, không bắt buộc) ----------

        /** Mã tham chiếu giao dịch ngân hàng (V2: reference; V1: tid). */
        private String reference;

        /** Số dư sau giao dịch (V2: runningBalance; V1: cusum_balance). */
        private BigDecimal runningBalance;

        /** Thời gian giao dịch từ ngân hàng (V2: transactionDateTime; V1: when). */
        private String transactionDateTime;

        /** Số tài khoản thụ hưởng. */
        private String accountNumber;

        /** Tên ngân hàng thụ hưởng (V2: bankName; V1: bankName). */
        private String bankName;

        /** Tên viết tắt ngân hàng (V2: bankAbbreviation; V1: bankAbbreviation). */
        private String bankAbbreviation;

        /** Số tài khoản ảo (V2: virtualAccountNumber; V1: virtualAccount). */
        private String virtualAccountNumber;

        /** Tên tài khoản ảo (V2: virtualAccountName; V1: virtualAccountName). */
        private String virtualAccountName;

        /** Tên chủ tài khoản đối ứng (V2: counterAccountName; V1: corresponsiveName). */
        private String counterAccountName;

        /** Số tài khoản đối ứng (V2: counterAccountNumber; V1: corresponsiveAccount). */
        private String counterAccountNumber;

        /** Mã ngân hàng đối ứng (V2: counterAccountBankId; V1: corresponsiveBankId). */
        private String counterAccountBankId;

        /** Tên ngân hàng đối ứng (V2: counterAccountBankName; V1: corresponsiveBankName). */
        private String counterAccountBankName;
    }
}
