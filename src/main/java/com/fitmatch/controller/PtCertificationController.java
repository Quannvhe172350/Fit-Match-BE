package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.service.PtCertificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pt/certifications")
@RequiredArgsConstructor
@Tag(name = "D. PT Certifications", description = "Chứng chỉ PT. Từ UC-020: Gym quản lý chứng chỉ qua /api/gym/pts/{ptId}/certifications; PT chỉ xem. Các thao tác ghi ở đây DEPRECATED.")
@SecurityRequirement(name = "bearerAuth")
public class PtCertificationController {

    private final PtCertificationService certificationService;

    /** @deprecated UC-020: chứng chỉ do Gym quản lý qua /api/gym/pts/{ptId}/certifications. */
    @Deprecated
    @Operation(deprecated = true, summary = "[DEPRECATED] Thêm chứng chỉ",
            description = "**DEPRECATED (UC-020)** — Gym quản lý chứng chỉ PT. Giữ tạm cho client cũ.")
    @PostMapping
    public ResponseEntity<ApiResponse<CertificationResponse>> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Certification added",
                        certificationService.add(userDetails.getUsername(), request)));
    }

    /** @deprecated UC-020: chứng chỉ do Gym quản lý. */
    @Deprecated
    @Operation(deprecated = true, summary = "[DEPRECATED] Cập nhật chứng chỉ",
            description = "**DEPRECATED (UC-020)** — Gym quản lý chứng chỉ PT. Giữ tạm cho client cũ.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CertificationResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certification updated",
                certificationService.update(userDetails.getUsername(), id, request)));
    }

    /** @deprecated UC-020: chứng chỉ do Gym quản lý. */
    @Deprecated
    @Operation(deprecated = true, summary = "[DEPRECATED] Xoá chứng chỉ",
            description = "**DEPRECATED (UC-020)** — Gym quản lý chứng chỉ PT. Giữ tạm cho client cũ.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        certificationService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Certification deleted", null));
    }

    @Operation(summary = "UC-007 — Liệt kê chứng chỉ của chính mình", description = "Actor: **PT**. Read-only; chứng chỉ do Gym ghi nhận (UC-020).")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CertificationResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(certificationService.list(userDetails.getUsername())));
    }
}
