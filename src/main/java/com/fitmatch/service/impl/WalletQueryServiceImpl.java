package com.fitmatch.service.impl;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WalletResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.WalletQueryService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public WalletResponse getForGym(String gymUsername) {
        return WalletResponse.of(requireWallet(gymUsername));
    }

    @Override
    @Transactional
    public PageResponse<WalletTransactionResponse> transactionsForGym(String gymUsername, Pageable pageable) {
        Wallet wallet = requireWallet(gymUsername);
        Page<com.fitmatch.entity.WalletTransaction> page =
                walletTransactionRepository.findByWallet_IdOrderByIdDesc(wallet.getId(), pageable);
        return PageResponse.of(page, WalletTransactionResponse::of);
    }

    /** Ví tạo lazy khi Gym (APPROVED) truy cập lần đầu — số dư 0. */
    private Wallet requireWallet(String gymUsername) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        return walletRepository.findByGymProfile_Id(gym.getId())
                .orElseGet(() -> walletService.getOrCreate(gym));
    }
}
