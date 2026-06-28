package com.fitmatch.service.impl;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.AuditLogResponse;
import com.fitmatch.entity.AuditLog;
import com.fitmatch.repository.AuditLogRepository;
import com.fitmatch.repository.spec.AuditLogSpecifications;
import com.fitmatch.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String action, String targetType, String actor,
                                                 LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(AuditLogSpecifications.hasAction(action))
                .and(AuditLogSpecifications.hasTargetType(targetType))
                .and(AuditLogSpecifications.byActor(actor))
                .and(AuditLogSpecifications.createdFrom(from))
                .and(AuditLogSpecifications.createdTo(to));
        return PageResponse.of(auditLogRepository.findAll(spec, pageable), AuditLogResponse::of);
    }
}
