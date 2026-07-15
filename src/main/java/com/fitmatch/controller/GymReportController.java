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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/gym/reports")
@RequiredArgsConstructor
@Tag(name = "K. Gym - Reports", description = "Báo cáo vận hành & tài chính của Gym (UC-076). Yêu cầu ROLE_GYM_OPERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymReportController {

    private final ReportService reportService;

    @Operation(
            summary = "UC-076 — Báo cáo vận hành của gym tôi",
            description = "Actor: **Gym Operator**. Booking theo trạng thái, dòng tiền của gym và số dư ví hiện tại theo khoảng ngày [from, to]. Lỗi: 409 gym chưa APPROVED.")
    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<OperationalReportResponse>> operational(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.gymReport(userDetails.getUsername(), from, to)));
    }
}
