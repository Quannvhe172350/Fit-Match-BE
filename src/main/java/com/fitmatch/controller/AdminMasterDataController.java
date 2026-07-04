package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.ServiceCategoryRequest;
import com.fitmatch.dto.admin.ServiceCategoryResponse;
import com.fitmatch.dto.admin.SystemConfigRequest;
import com.fitmatch.dto.admin.SystemConfigResponse;
import com.fitmatch.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/master-data")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Master Data",
        description = "Quản lý danh mục dịch vụ & cấu hình hệ thống (UC-078). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMasterDataController {

    private final MasterDataService masterDataService;

    @Operation(summary = "UC-078 — Tạo danh mục dịch vụ",
            description = "Actor: **Admin**. Lỗi: 400 tên trùng.")
    @PostMapping("/service-categories")
    public ResponseEntity<ApiResponse<ServiceCategoryResponse>> createCategory(
            @Valid @RequestBody ServiceCategoryRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Category created",
                masterDataService.createCategory(request, actor.getUsername())));
    }

    @Operation(summary = "UC-078 — Cập nhật danh mục dịch vụ",
            description = "Actor: **Admin**. Đổi tên/mô tả/bật tắt. Lỗi: 404 không tồn tại.")
    @PutMapping("/service-categories/{id}")
    public ResponseEntity<ApiResponse<ServiceCategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ServiceCategoryRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Category updated",
                masterDataService.updateCategory(id, request, actor.getUsername())));
    }

    @Operation(summary = "UC-078 — Danh sách danh mục dịch vụ",
            description = "Actor: **Admin**. includeInactive=true để xem cả danh mục đã tắt.")
    @GetMapping("/service-categories")
    public ResponseEntity<ApiResponse<List<ServiceCategoryResponse>>> listCategories(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success(masterDataService.listCategories(includeInactive)));
    }

    @Operation(summary = "UC-078 — Đặt tham số cấu hình hệ thống (upsert)",
            description = "Actor: **Admin**. Tạo mới hoặc ghi đè theo configKey (UPPER_SNAKE_CASE).")
    @PutMapping("/system-configs")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> upsertConfig(
            @Valid @RequestBody SystemConfigRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Config saved",
                masterDataService.upsertConfig(request, actor.getUsername())));
    }

    @Operation(summary = "UC-078 — Danh sách cấu hình hệ thống",
            description = "Actor: **Admin**.")
    @GetMapping("/system-configs")
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> listConfigs() {
        return ResponseEntity.ok(ApiResponse.success(masterDataService.listConfigs()));
    }
}
