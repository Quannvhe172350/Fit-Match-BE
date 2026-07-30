package com.fitmatch.service.impl;

import com.fitmatch.controller.PaymentDevController;
import com.fitmatch.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    /**
     * Hỏi thẳng container xem bean có tồn tại không, thay vì tự đọc lại danh sách
     * active profile: điều kiện bật/tắt nằm ở annotation của chính controller, đọc
     * profile ở đây là nhân đôi luật và sẽ lệch khi ai đó sửa một bên.
     * ObjectProvider cho phép phụ thuộc vào một bean có thể không tồn tại.
     */
    private final ObjectProvider<PaymentDevController> paymentSimulator;

    @Override
    public String getStatus() {
        return "UP";
    }

    @Override
    public boolean isPaymentSimulatorEnabled() {
        return paymentSimulator.getIfAvailable() != null;
    }
}
