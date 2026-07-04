package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.CreateGymPtRequest;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.dto.pt.UpdateGymPtRequest;
import com.fitmatch.service.GymPtManagementService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym/pts")
@RequiredArgsConstructor
@Tag(name = "G. Gym - PT Management",
        description = "Gym tạo và quản lý PT dưới quyền mình (UC-019..021). Yêu cầu ROLE_GYM_OPERATOR + Gym đã APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymPtController {

    private final GymPtManagementService service;

    @Operation(
            summary = "UC-019 — Tạo PT dưới quyền Gym",
            description = "Actor: **Gym Operator**. Tạo tài khoản (ROLE_PT) + hồ sơ PT thuộc Gym; PT ACTIVE ngay, không qua platform verification. Lỗi: 409 username/email trùng hoặc Gym chưa APPROVED; 404 chưa có hồ sơ Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<GymPtResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateGymPtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PT created", service.createPt(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-019 — Danh sách PT của Gym",
            description = "Actor: **Gym Operator**. Liệt kê PT thuộc Gym, phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GymPtResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(userDetails.getUsername(), pageable)));
    }

    @Operation(
            summary = "UC-019 — Chi tiết PT của Gym",
            description = "Actor: **Gym Operator**. Xem chi tiết một PT thuộc Gym. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GymPtResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-019 — Cập nhật hồ sơ PT của Gym",
            description = "Actor: **Gym Operator**. Cập nhật một phần hồ sơ PT (displayName/bio/specialization/serviceArea/experienceYears). Lỗi: 404 PT không thuộc Gym.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GymPtResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGymPtRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT profile updated",
                service.update(userDetails.getUsername(), id, request)));
    }
}
