package com.fitmatch.service;

import com.fitmatch.dto.admin.ServiceCategoryRequest;
import com.fitmatch.dto.admin.ServiceCategoryResponse;
import com.fitmatch.dto.admin.SystemConfigRequest;
import com.fitmatch.dto.admin.SystemConfigResponse;

import java.util.List;

/**
 * UC-078: Admin quản lý master data (danh mục dịch vụ) và tham số cấu hình hệ thống.
 */
public interface MasterDataService {

    ServiceCategoryResponse createCategory(ServiceCategoryRequest request, String actorUsername);

    ServiceCategoryResponse updateCategory(Long id, ServiceCategoryRequest request, String actorUsername);

    List<ServiceCategoryResponse> listCategories(boolean includeInactive);

    SystemConfigResponse upsertConfig(SystemConfigRequest request, String actorUsername);

    List<SystemConfigResponse> listConfigs();
}
