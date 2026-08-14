package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeEvidenceRequest;
import com.fitmatch.dto.dispute.DisputeEvidenceResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Tag(name = "I. Disputes", description = "Tranh chấp/khiếu nại của các bên liên quan (UC-063/064). Actor: Customer/Gym/PT của booking.")
@SecurityRequirement(name = "bearerAuth")
public class DisputeController {

    private final DisputeService disputeService;

    // Mở tranh chấp: POST /api/tickets/{id}/disputes?sessionId= — cần biết vé và
    // (tuỳ chọn) buổi tập vì mức đóng băng khác nhau theo cấp (câu 34).

    @Operation(
            summary = "UC-045 — Danh sách tranh chấp của tôi",
            description = "Actor: **Customer/Gym/PT**. Trả tranh chấp theo vai trò của người gọi.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DisputeResponse>>> myDisputes(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.myDisputes(userDetails.getUsername(), pageable)));
    }

    @Operation(
            summary = "UC-065 — Chi tiết tranh chấp",
            description = "Actor: **bên liên quan**. Lỗi: 404 không liên quan.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.detail(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-064 — Gửi bằng chứng",
            description = "Actor: **bên liên quan**. Mô tả + fileUrl (upload qua /api/files). Lỗi: 409 tranh chấp đã đóng; 404 không liên quan.")
    @PostMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<DisputeEvidenceResponse>> addEvidence(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody DisputeEvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Evidence submitted", disputeService.addEvidence(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-064 — Danh sách bằng chứng",
            description = "Actor: **bên liên quan**.")
    @GetMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<List<DisputeEvidenceResponse>>> evidence(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.evidence(userDetails.getUsername(), id)));
    }
}
