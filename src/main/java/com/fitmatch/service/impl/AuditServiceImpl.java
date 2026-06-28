package com.fitmatch.service.impl;

import com.fitmatch.entity.AuditLog;
import com.fitmatch.repository.AuditLogRepository;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void record(String action, String targetType, Object targetId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .targetType(targetType)
                .targetId(targetId == null ? null : String.valueOf(targetId))
                .description(description)
                .build());
    }
}
