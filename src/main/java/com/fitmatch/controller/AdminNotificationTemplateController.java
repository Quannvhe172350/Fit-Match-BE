package com.fitmatch.controller;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.NotificationTemplateDtos.TemplateResponse;
import com.fitmatch.dto.admin.NotificationTemplateDtos.TemplateUpdateRequest;
import com.fitmatch.entity.NotificationTemplate;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.NotificationTemplateRepository;
import com.fitmatch.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-075: quản trị template thông báo. Code seed bằng migration (V52) khớp
 * NotificationDispatcher — API chỉ cho update nội dung/bật-tắt, không tạo code mới
 * (code mới không có sự kiện nào phát thì chỉ là rác).
 */
@RestController
@RequestMapping("/api/admin/notification-templates")
@RequiredArgsConstructor
@Tag(name = "Z10. Admin - Notification Templates",
        description = "Template thông báo theo sự kiện (UC-075). Tắt/thiếu template -> dùng văn bản mặc định. ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationTemplateController {

    private final NotificationTemplateRepository templateRepository;
    private final AuditService auditService;

    @Operation(summary = "UC-075 — Danh sách template thông báo")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                templateRepository.findAll().stream().map(TemplateResponse::of).toList()));
    }

    @Operation(summary = "UC-075 — Cập nhật template",
            description = "Sửa title/body (placeholder {key} giữ nguyên khi render nếu thiếu giá trị) hoặc bật/tắt. Audit NOTIFICATION_TEMPLATE_CHANGE.")
    @PutMapping("/{code}")
    @Transactional
    public ResponseEntity<ApiResponse<TemplateResponse>> update(
            @PathVariable String code,
            @Valid @RequestBody TemplateUpdateRequest request) {
        NotificationTemplate t = templateRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification template", code));
        t.setTitle(request.getTitle());
        t.setBody(request.getBody());
        t.setEnabled(request.getEnabled());
        t = templateRepository.save(t);
        auditService.record(AuditActions.NOTIFICATION_TEMPLATE_CHANGE, "NotificationTemplate", code,
                "Updated (enabled=" + request.getEnabled() + ")");
        return ResponseEntity.ok(ApiResponse.success("Template updated", TemplateResponse.of(t)));
    }
}
