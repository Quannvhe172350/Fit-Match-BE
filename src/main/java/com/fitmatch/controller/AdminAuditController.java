package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.AuditLogResponse;
import com.fitmatch.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "B. Admin - Audit Logs", description = "Xem & lọc nhật ký audit (UC-13). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogQueryService auditLogQueryService;

    @Operation(
            summary = "UC-13 — Xem / lọc audit logs",
            description = """
                    Actor: **Admin**. Lọc theo action, targetType, actor (người thực hiện), khoảng thời gian
                    (from/to dạng ISO-8601). Mặc định sắp xếp mới nhất trước. Read-only.
                    Lỗi: 401 chưa đăng nhập; 403 không phải Admin.
                    """)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @Parameter(description = "Mã hành động, vd USER_LOCK") @RequestParam(required = false) String action,
            @Parameter(description = "Loại đối tượng, vd User") @RequestParam(required = false) String targetType,
            @Parameter(description = "Username người thực hiện") @RequestParam(required = false) String actor,
            @Parameter(description = "Từ thời điểm (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Đến thời điểm (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogQueryService.search(action, targetType, actor, from, to, pageable)));
    }
}
