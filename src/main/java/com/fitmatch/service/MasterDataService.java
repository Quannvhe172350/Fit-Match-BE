package com.fitmatch.service;

import com.fitmatch.dto.admin.ServiceCategoryRequest;
import com.fitmatch.dto.admin.ServiceCategoryResponse;

import java.util.List;

/**
 * UC-078: Admin quản lý master data (danh mục dịch vụ).
 * E-8 (quyết định 2026-07-17): system-configs đã gỡ — write-only, không logic nào đọc.
 */
public interface MasterDataService {

    ServiceCategoryResponse createCategory(ServiceCategoryRequest request, String actorUsername);

    ServiceCategoryResponse updateCategory(Long id, ServiceCategoryRequest request, String actorUsername);

    List<ServiceCategoryResponse> listCategories(boolean includeInactive);
}
