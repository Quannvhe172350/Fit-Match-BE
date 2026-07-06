package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.BookingRulesDto;
import com.fitmatch.dto.gym.TrainingPackageRequest;
import com.fitmatch.dto.gym.TrainingPackageResponse;
import com.fitmatch.dto.gym.UpdateCatalogStatusRequest;
import com.fitmatch.service.TrainingPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/packages")
@RequiredArgsConstructor
@Tag(name = "G. Gym Packages", description = "Quản lý gói tập của Gym (UC-025). Yêu cầu ROLE_GYM_OPERATOR + Gym APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class TrainingPackageController {

    private final TrainingPackageService trainingPackageService;

    @Operation(summary = "UC-025 — Tạo gói tập",
            description = "Actor: **Gym Operator**. Gói gồm số buổi, hạn dùng (ngày), giá, điều kiện; có thể gắn một dịch vụ của chính Gym. Lỗi: 409 Gym chưa APPROVED; 404 dịch vụ không thuộc Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<TrainingPackageResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TrainingPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Training package created",
                trainingPackageService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-025 — Cập nhật gói tập",
            description = "Actor: **Gym Operator**. Lỗi: 404 gói không thuộc Gym.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TrainingPackageResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody TrainingPackageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Training package updated",
                trainingPackageService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-025 — Ngưng bán gói tập",
            description = "Actor: **Gym Operator**. Đặt active=false (không xoá dữ liệu). Lỗi: 404 gói không thuộc Gym.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        trainingPackageService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Training package deactivated", null));
    }

    @Operation(summary = "UC-025 — Danh sách gói tập của Gym",
            description = "Actor: **Gym Operator**.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TrainingPackageResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(trainingPackageService.list(userDetails.getUsername())));
    }

    @Operation(summary = "UC-025 — Chi tiết gói tập",
            description = "Actor: **Gym Operator**. Lỗi: 404 gói không thuộc Gym.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrainingPackageResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(trainingPackageService.detail(userDetails.getUsername(), id)));
    }

    @Operation(summary = "UC-026 — Cấu hình quy tắc thanh toán/đặt lịch cho gói tập",
            description = "Actor: **Gym Operator**. depositPercent (0-100, null = trả đủ), freeCancellationHours, minNoticeHours. Lỗi: 404 gói không thuộc Gym.")
    @PutMapping("/{id}/booking-rules")
    public ResponseEntity<ApiResponse<TrainingPackageResponse>> updateBookingRules(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BookingRulesDto request) {
        return ResponseEntity.ok(ApiResponse.success("Booking rules updated",
                trainingPackageService.updateBookingRules(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-027 — Đổi trạng thái vòng đời gói tập",
            description = "Actor: **Gym Operator**. PUBLISHED / HIDDEN / PAUSED / ARCHIVED; ARCHIVED là trạng thái cuối. Lỗi: 409 đã ARCHIVED; 404 gói không thuộc Gym.")
    @PatchMapping("/{id}/catalog-status")
    public ResponseEntity<ApiResponse<TrainingPackageResponse>> updateCatalogStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCatalogStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Catalog status updated",
                trainingPackageService.updateCatalogStatus(userDetails.getUsername(), id, request.getStatus())));
    }
}
