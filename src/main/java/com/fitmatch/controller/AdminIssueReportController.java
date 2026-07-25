package com.fitmatch.controller;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.DecisionNoteRequest;
import com.fitmatch.dto.report.IssueReportResponse;
import com.fitmatch.service.IssueReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/issue-reports")
@RequiredArgsConstructor
@Tag(name = "Z8. Admin - Issue Reports",
        description = "Kiểm duyệt báo cáo vấn đề dịch vụ/hành vi (UC-070/071). ADMIN hoặc MODERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
public class AdminIssueReportController {

    private final IssueReportService issueReportService;

    @Operation(summary = "UC-071 — Hàng đợi báo cáo vấn đề")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IssueReportResponse>>> queue(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(issueReportService.queue(status, pageable)));
    }

    @Operation(summary = "UC-071 — Xử lý báo cáo (đã có hành động)")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<IssueReportResponse>> resolve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Report resolved",
                issueReportService.resolve(userDetails.getUsername(), id,
                        request != null ? request.getNote() : null)));
    }

    @Operation(summary = "UC-071 — Bỏ qua báo cáo (không vi phạm)")
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<IssueReportResponse>> dismiss(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Report dismissed",
                issueReportService.dismiss(userDetails.getUsername(), id,
                        request != null ? request.getNote() : null)));
    }
}
