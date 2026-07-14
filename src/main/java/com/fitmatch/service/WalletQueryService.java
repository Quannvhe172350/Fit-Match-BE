package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WalletResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import org.springframework.data.domain.Pageable;

/** Đọc ví Gym: số dư và lịch sử bút toán (UC-061). */
public interface WalletQueryService {

    WalletResponse getForGym(String gymUsername);

    PageResponse<WalletTransactionResponse> transactionsForGym(String gymUsername, Pageable pageable);
}
