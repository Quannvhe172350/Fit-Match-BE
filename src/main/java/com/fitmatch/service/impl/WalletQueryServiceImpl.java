package com.fitmatch.service.impl;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WalletResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import com.fitmatch.entity.Wallet;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.WalletQueryService;
import com.fitmatch.service.support.WalletOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletOwnerResolver walletOwnerResolver;

    @Override
    @Transactional
    public WalletResponse getForOwner(String username, WalletOwnerType ownerType) {
        return WalletResponse.of(walletOwnerResolver.resolve(username, ownerType));
    }

    @Override
    @Transactional
    public PageResponse<WalletTransactionResponse> transactionsForOwner(String username,
                                                                        WalletOwnerType ownerType,
                                                                        Pageable pageable) {
        Wallet wallet = walletOwnerResolver.resolve(username, ownerType);
        Page<com.fitmatch.entity.WalletTransaction> page =
                walletTransactionRepository.findByWallet_IdOrderByIdDesc(wallet.getId(), pageable);
        return PageResponse.of(page, WalletTransactionResponse::of);
    }
}
