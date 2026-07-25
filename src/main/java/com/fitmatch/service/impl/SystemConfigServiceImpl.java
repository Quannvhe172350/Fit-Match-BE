package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.admin.SystemConfigResponse;
import com.fitmatch.entity.SystemConfig;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.SystemConfigRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResponse> list() {
        return systemConfigRepository.findAll().stream()
                .map(SystemConfigResponse::of)
                .toList();
    }

    @Override
    @Transactional
    public SystemConfigResponse update(String key, String value) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("System config", key));
        // Các key hiện tại đều là số nguyên dương (ngày/giờ) — chặn giá trị vô nghĩa.
        // Khi thêm key dạng khác thì mở rộng validate theo key ở đây.
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Value must be a number");
        }
        if (parsed <= 0 || parsed > 3650) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Value must be between 1 and 3650");
        }
        String old = config.getConfigValue();
        config.setConfigValue(String.valueOf(parsed));
        config = systemConfigRepository.save(config);
        auditService.record(AuditActions.SYSTEM_CONFIG_CHANGE, "SystemConfig", key,
                old + " -> " + parsed);
        log.info("System config {} changed {} -> {}", key, old, parsed);
        return SystemConfigResponse.of(config);
    }

    @Override
    @Transactional(readOnly = true)
    public Long findLong(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(c -> {
                    try {
                        return Long.parseLong(c.getConfigValue().trim());
                    } catch (NumberFormatException e) {
                        log.warn("System config {} has non-numeric value '{}' - ignored",
                                key, c.getConfigValue());
                        return null;
                    }
                })
                .orElse(null);
    }
}
