package com.fitmatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình thanh toán VietQR + Casso (UC-052/053).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class PaymentProperties {

    private final Vietqr vietqr = new Vietqr();
    private final Casso casso = new Casso();
    private final Payment payment = new Payment();

    @Getter
    @Setter
    public static class Vietqr {
        private String bankBin;
        private String accountNo;
        private String accountName;
        private String template = "compact2";
    }

    @Getter
    @Setter
    public static class Casso {
        /** Secret xác thực webhook Casso V2 — HMAC-SHA256 qua header X-Casso-Signature. */
        private String webhookSecret;
    }

    @Getter
    @Setter
    public static class Payment {
        private int orderTtlHours = 24;
    }
}
