package com.fitmatch.service;

import com.fitmatch.dto.payment.CassoWebhookRequest;

/**
 * Xử lý webhook đối soát từ Casso (UC-053).
 */
public interface PaymentWebhookService {

    /** Trả về số giao dịch khớp và ghi nhận thanh toán thành công. */
    int processCasso(CassoWebhookRequest request);
}
