package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.ServiceCategoryResponse;
import com.fitmatch.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BE-7/B-21 (audit 2026-07-17): trước đây danh mục dịch vụ chỉ đọc được qua
 * /api/admin/master-data (khóa ADMIN) — gym operator không thể phân loại dịch vụ
 * (UC-024 "category từ master data" không khả thi end-to-end).
 */
@RestController
@RequestMapping("/api/gym/service-categories")
@RequiredArgsConstructor
@Tag(name = "F. Gym Catalog", description = "UC-024: danh mục dịch vụ (master data) cho Gym Operator — chỉ đọc.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymServiceCategoryController {

    private final MasterDataService masterDataService;

    @Operation(
            summary = "UC-024 — Danh mục dịch vụ khả dụng",
            description = "Actor: **Gym Operator**. Chỉ trả danh mục đang bật; quản trị danh mục thuộc Admin (UC-078).")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceCategoryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(masterDataService.listCategories(false)));
    }
}
