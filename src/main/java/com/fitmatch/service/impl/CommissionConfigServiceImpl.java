package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.dto.admin.CommissionConfigRequest;
import com.fitmatch.dto.admin.CommissionConfigResponse;
import com.fitmatch.entity.CommissionConfig;
import com.fitmatch.repository.CommissionConfigRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.CommissionConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionConfigServiceImpl implements CommissionConfigService {

    /**
     * Mặc định khi chưa cấu hình: hoa hồng 15%, phí 0%, giữ tiền 7 ngày.
     *
     * <p>D-18: 7 ngày để bằng {@code app.dispute.open-window-days} — tiền phải còn
     * nằm trong hệ thống suốt thời gian khách còn quyền mở tranh chấp.
     */
    private static final BigDecimal DEFAULT_COMMISSION = new BigDecimal("15.00");
    private static final BigDecimal DEFAULT_FEE = new BigDecimal("0.00");
    private static final int DEFAULT_HOLD_DAYS = 7;

    private final CommissionConfigRepository commissionConfigRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public CommissionConfig currentConfig() {
        return commissionConfigRepository.findTopByOrderByIdDesc()
                .orElseGet(() -> CommissionConfig.builder()
                        .commissionPercent(DEFAULT_COMMISSION)
                        .platformFeePercent(DEFAULT_FEE)
                        .settlementHoldDays(DEFAULT_HOLD_DAYS)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionConfigResponse get() {
        return CommissionConfigResponse.of(currentConfig());
    }

    @Override
    @Transactional
    public CommissionConfigResponse update(CommissionConfigRequest request, String actorUsername) {
        CommissionConfig saved = commissionConfigRepository.save(CommissionConfig.builder()
                .commissionPercent(request.getCommissionPercent())
                .platformFeePercent(request.getPlatformFeePercent())
                .settlementHoldDays(request.getSettlementHoldDays())
                .build());
        auditService.record(AuditActions.COMMISSION_CONFIG_CHANGE, "CommissionConfig", saved.getId(),
                "Commission config updated by " + actorUsername + ": commission="
                        + request.getCommissionPercent() + "%, fee=" + request.getPlatformFeePercent()
                        + "%, hold=" + request.getSettlementHoldDays() + "d");
        log.info("Commission config updated by {} (id {})", actorUsername, saved.getId());
        return CommissionConfigResponse.of(saved);
    }
}
