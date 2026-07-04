package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.admin.ServiceCategoryRequest;
import com.fitmatch.dto.admin.ServiceCategoryResponse;
import com.fitmatch.dto.admin.SystemConfigRequest;
import com.fitmatch.dto.admin.SystemConfigResponse;
import com.fitmatch.entity.ServiceCategory;
import com.fitmatch.entity.SystemConfig;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.ServiceCategoryRepository;
import com.fitmatch.repository.SystemConfigRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataServiceImpl implements MasterDataService {

    private final ServiceCategoryRepository serviceCategoryRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public ServiceCategoryResponse createCategory(ServiceCategoryRequest request, String actorUsername) {
        if (serviceCategoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Category '" + request.getName() + "' already exists");
        }
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(request.getActive() == null || request.getActive())
                .build());
        auditService.record(AuditActions.MASTER_DATA_CHANGE, "ServiceCategory", category.getId(),
                "Category created by " + actorUsername);
        return ServiceCategoryResponse.of(category);
    }

    @Override
    @Transactional
    public ServiceCategoryResponse updateCategory(Long id, ServiceCategoryRequest request, String actorUsername) {
        ServiceCategory category = serviceCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        serviceCategoryRepository.save(category);
        auditService.record(AuditActions.MASTER_DATA_CHANGE, "ServiceCategory", id,
                "Category updated by " + actorUsername);
        return ServiceCategoryResponse.of(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> listCategories(boolean includeInactive) {
        List<ServiceCategory> categories = includeInactive
                ? serviceCategoryRepository.findAll()
                : serviceCategoryRepository.findByActiveTrue();
        return categories.stream().map(ServiceCategoryResponse::of).toList();
    }

    @Override
    @Transactional
    public SystemConfigResponse upsertConfig(SystemConfigRequest request, String actorUsername) {
        SystemConfig config = systemConfigRepository.findByConfigKey(request.getConfigKey())
                .orElseGet(() -> SystemConfig.builder().configKey(request.getConfigKey()).build());
        config.setConfigValue(request.getConfigValue());
        config.setDescription(request.getDescription());
        config = systemConfigRepository.save(config);
        auditService.record(AuditActions.SYSTEM_CONFIG_CHANGE, "SystemConfig", config.getId(),
                "Config " + request.getConfigKey() + " set by " + actorUsername);
        log.info("System config {} updated by {}", request.getConfigKey(), actorUsername);
        return SystemConfigResponse.of(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResponse> listConfigs() {
        return systemConfigRepository.findAll().stream().map(SystemConfigResponse::of).toList();
    }
}
