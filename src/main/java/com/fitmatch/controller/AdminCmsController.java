package com.fitmatch.controller;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.cms.CmsContentRequest;
import com.fitmatch.dto.cms.CmsContentResponse;
import com.fitmatch.service.CmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/cms")
@RequiredArgsConstructor
@Tag(name = "L. Admin - CMS", description = "Quản lý nội dung CMS & chiến dịch nổi bật (UC-074). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCmsController {

    private final CmsService cmsService;

    @Operation(summary = "UC-074 — Danh sách nội dung (lọc theo loại nếu truyền)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CmsContentResponse>>> list(
            @Parameter(description = "Loại (bỏ trống = tất cả)") @RequestParam(required = false) CmsType type) {
        return ResponseEntity.ok(ApiResponse.success(cmsService.adminList(type)));
    }

    @Operation(summary = "UC-074 — Tạo nội dung")
    @PostMapping
    public ResponseEntity<ApiResponse<CmsContentResponse>> create(@Valid @RequestBody CmsContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Content created", cmsService.create(request)));
    }

    @Operation(summary = "UC-074 — Cập nhật nội dung")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CmsContentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CmsContentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Content updated", cmsService.update(id, request)));
    }

    @Operation(summary = "UC-074 — Xoá nội dung")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        cmsService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Content deleted", null));
    }
}
