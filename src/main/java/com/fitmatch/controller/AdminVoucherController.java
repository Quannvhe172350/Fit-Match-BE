package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.voucher.VoucherRequest;
import com.fitmatch.dto.voucher.VoucherResponse;
import com.fitmatch.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/vouchers")
@RequiredArgsConstructor
@Tag(name = "L. Admin - Vouchers", description = "Quản lý voucher/khuyến mãi (UC-073). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "UC-073 — Danh sách voucher")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.list(pageable)));
    }

    @Operation(summary = "UC-073 — Tạo voucher",
            description = "code duy nhất; PERCENT có thể kèm maxDiscount; usageLimit/validFrom/validTo tùy chọn.")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Voucher created", voucherService.create(request)));
    }

    @Operation(summary = "UC-073 — Cập nhật voucher")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long id, @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Voucher updated", voucherService.update(id, request)));
    }

    @Operation(summary = "UC-073 — Bật/tắt voucher")
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<VoucherResponse>> setActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Voucher updated", voucherService.setActive(id, active)));
    }
}
