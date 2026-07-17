package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.service.AdminWalletService;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWalletServiceImpl implements AdminWalletService {

    private final WalletService walletService;
    private final AuditService auditService;

    @Override
    @Transactional
    public void freeze(Long gymProfileId, BigDecimal amount, String reason, String actorUsername) {
        walletService.freeze(gymProfileId, amount, "Admin freeze: " + reason);
        auditService.record(AuditActions.WALLET_FREEZE, "Wallet", gymProfileId,
                "Frozen " + amount + " by " + actorUsername + ": " + reason);
        log.info("Wallet of gym {} frozen {} by {}", gymProfileId, amount, actorUsername);
    }

    @Override
    @Transactional
    public void unfreeze(Long gymProfileId, BigDecimal amount, String reason, String actorUsername) {
        walletService.unfreeze(gymProfileId, amount, "Admin unfreeze: " + reason);
        auditService.record(AuditActions.WALLET_UNFREEZE, "Wallet", gymProfileId,
                "Unfrozen " + amount + " by " + actorUsername + ": " + reason);
        log.info("Wallet of gym {} unfrozen {} by {}", gymProfileId, amount, actorUsername);
    }
}
