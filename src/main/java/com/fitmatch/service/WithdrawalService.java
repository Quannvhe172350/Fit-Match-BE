package com.fitmatch.service;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import org.springframework.data.domain.Pageable;

/**
 * Rút tiền từ ví (UC-062): request (giữ chỗ available -> frozen) → admin/finance
 * duyệt (sinh QR VietQR trỏ tới tài khoản người thụ hưởng) → admin quét QR
 * chuyển khoản → mark-paid (frozen rời nền tảng); reject trả lại available.
 * <p>
 * V61 — áp dụng cho cả ví Gym, ví PT và ví khách hàng. Bước mark-paid có thể do
 * webhook Casso tự thực hiện khi khớp được giao dịch CHI trên sao kê, xem
 * {@link PaymentWebhookService}.
 */
public interface WithdrawalService {

    /** Tạo lệnh rút cho chủ ví tương ứng vai trò {@code ownerType} của user. */
    WithdrawalResponse create(String username, WalletOwnerType ownerType, WithdrawalCreateRequest request);

    /** Lệnh rút của chính người dùng, theo vai trò họ đang thao tác. */
    PageResponse<WithdrawalResponse> listForOwner(String username, WalletOwnerType ownerType, Pageable pageable);

    /** Hàng đợi Finance; {@code status}/{@code ownerType} null = không lọc chiều đó. */
    PageResponse<WithdrawalResponse> listForAdmin(WithdrawalStatus status, WalletOwnerType ownerType,
                                                  Pageable pageable);

    /** Duyệt lệnh và sinh QR chuyển khoản cho admin quét. */
    WithdrawalResponse approve(Long id, String note, String actorUsername);

    WithdrawalResponse reject(Long id, String note, String actorUsername);

    /** D-11: payoutReference (mã giao dịch chuyển khoản) bắt buộc để đối soát sao kê. */
    WithdrawalResponse markPaid(Long id, String payoutReference, String note, String actorUsername);

    /**
     * V61 — mark-paid do webhook Casso kích hoạt sau khi khớp giao dịch CHI.
     * Tách riêng khỏi {@link #markPaid} vì không có admin nào bấm: actor là hệ
     * thống và bản ghi được đánh dấu {@code autoMatched} để phân biệt khi kiểm toán.
     *
     * @param cassoTxnId id giao dịch Casso, dùng làm payoutReference
     */
    WithdrawalResponse markPaidByReconciliation(Long id, String cassoTxnId);
}
