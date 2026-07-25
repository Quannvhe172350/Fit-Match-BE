package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.report.IssueReportRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issue-reports")
@RequiredArgsConstructor
@Tag(name = "O. Issue Reports",
        description = "Báo cáo vấn đề dịch vụ/hành vi cho Gym/PT/Booking (UC-070).")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('CUSTOMER','GYM_OPERATOR','PT')")
public class IssueReportController {

    private final IssueReportService issueReportService;

    @Operation(summary = "UC-070 — Mở báo cáo vấn đề",
            description = "Actor: **Customer / Gym Operator / PT**. Mỗi người một báo cáo đang mở cho mỗi đối tượng; BOOKING chỉ các bên liên quan.")
    @PostMapping
    public ResponseEntity<ApiResponse<IssueReportResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody IssueReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Report submitted",
                issueReportService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-070 — Báo cáo của tôi")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<IssueReportResponse>>> my(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                issueReportService.my(userDetails.getUsername(), pageable)));
    }
}
