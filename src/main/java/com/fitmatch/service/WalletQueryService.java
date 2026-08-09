package com.fitmatch.service;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WalletResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import org.springframework.data.domain.Pageable;

/**
 * Đọc số dư và sổ cái ví (UC-061). Ví được tạo lazy ở lần truy cập đầu nên
 * người dùng chưa từng có dòng tiền nào vẫn mở được trang ví với số dư 0.
 */
public interface WalletQueryService {

    /** Số dư 4 bucket của ví ứng với vai trò người dùng đang thao tác. */
    WalletResponse getForOwner(String username, WalletOwnerType ownerType);

    /** Sổ cái append-only của ví đó, mới nhất trước. */
    PageResponse<WalletTransactionResponse> transactionsForOwner(String username, WalletOwnerType ownerType,
                                                                 Pageable pageable);
}
