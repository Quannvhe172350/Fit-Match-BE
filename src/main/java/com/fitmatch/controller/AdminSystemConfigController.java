package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.SystemConfigResponse;
import com.fitmatch.dto.admin.SystemConfigUpdateRequest;
import com.fitmatch.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system-configs")
@RequiredArgsConstructor
@Tag(name = "Z9. Admin - System Configs",
        description = "Tham số hệ thống chỉnh runtime (UC-078). Chỉ update key đã seed — không tạo key mới. ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "UC-078 — Danh sách tham số hệ thống")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.list()));
    }

    @Operation(summary = "UC-078 — Cập nhật giá trị tham số",
            description = "Chỉ nhận key đã tồn tại; giá trị số nguyên dương. Ghi audit SYSTEM_CONFIG_CHANGE.")
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> update(
            @PathVariable String key,
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Config updated",
                systemConfigService.update(key, request.getValue())));
    }
}
