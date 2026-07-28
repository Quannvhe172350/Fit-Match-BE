package com.fitmatch.service;

public interface HealthService {

    String getStatus();

    /**
     * BUG-11: endpoint mô phỏng thanh toán ({@code PaymentDevController}) bị gate
     * bằng {@code @Profile({"local","dev"})}, nên chạy BE ở profile khác là nó
     * không tồn tại. Trước đây FE tự đoán bằng biến env riêng của mình và vẫn hiện
     * nút — bấm vào thì lỗi. Cờ này để FE hỏi đúng nguồn sự thật là BE.
     */
    boolean isPaymentSimulatorEnabled();
}
