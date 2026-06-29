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
@Tag(name = "D. PT Certifications", description = "Quản lý chứng chỉ PT (UC-27)")
@SecurityRequirement(name = "bearerAuth")
public class PtCertificationController {

    private final PtCertificationService certificationService;

    @Operation(summary = "UC-27 — Thêm chứng chỉ", description = "Actor: **PT**. Lỗi: 404 chưa có hồ sơ PT.")
    @PostMapping
    public ResponseEntity<ApiResponse<CertificationResponse>> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Certification added",
                        certificationService.add(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-27 — Cập nhật chứng chỉ", description = "Actor: **PT** (chủ sở hữu). Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CertificationResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certification updated",
                certificationService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-27 — Xoá chứng chỉ", description = "Actor: **PT** (chủ sở hữu). Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        certificationService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Certification deleted", null));
    }

    @Operation(summary = "UC-27 — Liệt kê chứng chỉ", description = "Actor: **PT**. Trả về chứng chỉ của chính mình.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CertificationResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(certificationService.list(userDetails.getUsername())));
    }
}
