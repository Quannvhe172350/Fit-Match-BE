package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;
import com.fitmatch.service.GymFacilityService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/facilities")
@RequiredArgsConstructor
@Tag(name = "G. Gym Facilities", description = "Quản lý cơ sở vật chất của Gym (UC-47 → UC-49)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymFacilityController {

    private final GymFacilityService facilityService;

    @Operation(
            summary = "UC-47 — Tạo cơ sở vật chất",
            description = "Actor: **Gym Operator** (Gym đã APPROVED). Lỗi: 409 Gym chưa được duyệt; 404 chưa có hồ sơ Gym; 403 không phải Gym Operator.")
    @PostMapping
    public ResponseEntity<ApiResponse<FacilityResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FacilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Facility created", facilityService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-48 — Cập nhật cơ sở vật chất", description = "Actor: **Gym Operator** (chủ sở hữu). Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FacilityResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody FacilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Facility updated",
                facilityService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-48 — Vô hiệu hoá cơ sở vật chất", description = "Actor: **Gym Operator** (chủ sở hữu). Đặt active=false. Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        facilityService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Facility deactivated", null));
    }
}
