package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.report.OperationalReportResponse;
import com.fitmatch.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "K. Admin - Reports", description = "Báo cáo vận hành & tài chính toàn nền tảng (UC-076). Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    @Operation(
            summary = "UC-076 — Báo cáo vận hành toàn nền tảng",
            description = "Actor: **Admin/Finance**. Thống kê booking theo trạng thái, dòng tiền (giữ/hoàn/giải ngân/hoa hồng) và tranh chấp theo khoảng ngày [from, to].")
    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<OperationalReportResponse>> operational(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.platformReport(from, to)));
    }
}
