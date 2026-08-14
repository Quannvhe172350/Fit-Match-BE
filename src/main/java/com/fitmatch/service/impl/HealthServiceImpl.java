package com.fitmatch.service.impl;

import com.fitmatch.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    @Override
    public String getStatus() {
        return "UP";
    }

    /**
     * Bộ giả lập thanh toán đã bị gỡ cùng luồng booking cũ — mô hình vé xác nhận
     * tiền qua webhook Casso. Giữ lại cờ (luôn false) để client cũ không vỡ khi
     * đọc /health.
     */
    @Override
    public boolean isPaymentSimulatorEnabled() {
        return false;
    }
}
