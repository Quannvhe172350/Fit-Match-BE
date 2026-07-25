package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.measurement.BodyMeasurementRequest;
import com.fitmatch.dto.measurement.BodyMeasurementResponse;
import com.fitmatch.service.BodyMeasurementService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
@RequiredArgsConstructor
@Tag(name = "N. Body Measurements",
        description = "Số đo cơ thể — khách tự theo dõi tiến trình tập luyện (UC-051). Yêu cầu ROLE_CUSTOMER.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class BodyMeasurementController {

    private final BodyMeasurementService bodyMeasurementService;

    @Operation(summary = "UC-051 — Lịch sử số đo",
            description = "Actor: **Customer**. Phân trang, mặc định mới nhất trước.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BodyMeasurementResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "measuredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                bodyMeasurementService.list(userDetails.getUsername(), pageable)));
    }

    @Operation(summary = "UC-051 — Ghi nhận số đo mới")
    @PostMapping
    public ResponseEntity<ApiResponse<BodyMeasurementResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BodyMeasurementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Measurement recorded",
                bodyMeasurementService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-051 — Sửa số đo (chỉ của chính mình)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BodyMeasurementResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BodyMeasurementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Measurement updated",
                bodyMeasurementService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-051 — Xóa số đo (chỉ của chính mình)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        bodyMeasurementService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Measurement deleted", null));
    }
}
