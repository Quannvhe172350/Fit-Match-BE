package com.fitmatch.service;

import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import org.springframework.data.domain.Pageable;

/**
 * Rút tiền của Gym (UC-062): request (giữ chỗ available -> frozen) → admin/finance
 * duyệt → chuyển khoản thủ công → mark-paid (frozen rời nền tảng); reject trả lại available.
 */
public interface WithdrawalService {

    WithdrawalResponse create(String gymUsername, WithdrawalCreateRequest request);

    PageResponse<WithdrawalResponse> listForGym(String gymUsername, Pageable pageable);

    PageResponse<WithdrawalResponse> listForAdmin(WithdrawalStatus status, Pageable pageable);

    WithdrawalResponse approve(Long id, String note, String actorUsername);

    WithdrawalResponse reject(Long id, String note, String actorUsername);

    /** D-11: payoutReference (mã giao dịch chuyển khoản) bắt buộc để đối soát sao kê. */
    WithdrawalResponse markPaid(Long id, String payoutReference, String note, String actorUsername);
}
