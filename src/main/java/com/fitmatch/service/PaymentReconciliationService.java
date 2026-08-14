package com.fitmatch.service;

import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentTransactionResponse;
import com.fitmatch.dto.payment.ReconciliationSummaryResponse;
import org.springframework.data.domain.Pageable;

/**
 * Đối soát thủ công tiền vào tài khoản nền tảng (UC-053, UC-056 phần adjustment).
 * <p>
 * Webhook Casso chỉ tự động xác nhận được giao dịch khớp refCode và đủ tiền.
 * Bốn ca còn lại — sai nội dung chuyển khoản, thiếu tiền, tiền vào sau khi đơn
 * hết hạn, chuyển khoản trùng — cùng ca chuyển thừa đều để lại tiền thật trong
 * tài khoản mà không gắn được vào booking. Service này là hàng đợi xử lý những
 * ca đó: Finance xem, gắn vào booking hoặc ghi nhận đã trả lại người gửi.
 */
public interface PaymentReconciliationService {

    /** Danh sách giao dịch theo trạng thái đối soát / loại bất thường (null = không lọc). */
    PageResponse<PaymentTransactionResponse> list(ReconStatus reconStatus,
                                                  PaymentTxnAnomaly anomaly,
                                                  Pageable pageable);

    /** Thống kê hàng đợi NEEDS_REVIEW theo loại bất thường. */
    ReconciliationSummaryResponse summary();

    /**
     * Gắn giao dịch vào một booking đang PENDING_PAYMENT và ghi nhận giữ tiền —
     * đi đúng luồng {@code confirmPaymentHold} (hold ví, đơn -> PAID, chuyển
     * booking cho Gym, thông báo khách + Gym), nên số dư ví luôn khớp đơn.
     *
     * @param allowAmountMismatch bỏ qua kiểm tra "giao dịch phải đủ số phải trả"
     *                            cho ca khách chuyển nhiều lần; bắt buộc có note
     */
    PaymentTransactionResponse applyToTicket(Long transactionId, Long ticketId,
                                              boolean allowAmountMismatch,
                                              String note, String actorUsername);

    /**
     * Tất toán giao dịch không gắn vào booking: đã chuyển trả người gửi
     * (RESOLVED_REFUNDED) hoặc xác định không cần hành động (RESOLVED_IGNORED).
     */
    PaymentTransactionResponse resolve(Long transactionId, ReconStatus resolution,
                                       String note, String actorUsername);
}
