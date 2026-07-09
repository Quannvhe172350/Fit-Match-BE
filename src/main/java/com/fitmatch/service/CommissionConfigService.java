package com.fitmatch.service;

import com.fitmatch.dto.admin.CommissionConfigRequest;
import com.fitmatch.dto.admin.CommissionConfigResponse;
import com.fitmatch.entity.CommissionConfig;

/**
 * UC-072: cấu hình kinh tế nền tảng (hoa hồng, phí, holding period).
 */
public interface CommissionConfigService {

    /** Cấu hình đang hiệu lực (entity) — dùng nội bộ cho settlement math. */
    CommissionConfig currentConfig();

    CommissionConfigResponse get();

    CommissionConfigResponse update(CommissionConfigRequest request, String actorUsername);
}
