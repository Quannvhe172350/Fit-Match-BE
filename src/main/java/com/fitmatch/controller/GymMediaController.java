package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymMediaRequest;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.service.GymMediaService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/media")
@RequiredArgsConstructor
@Tag(name = "G. Gym Media", description = "Ảnh/media công khai của Gym và chi nhánh (UC-016). Yêu cầu ROLE_GYM_OPERATOR + Gym APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymMediaController {

    private final GymMediaService gymMediaService;

    @Operation(summary = "UC-016 — Thêm ảnh cho Gym/chi nhánh",
            description = "Actor: **Gym Operator**. URL lấy từ /api/files/upload; branchId null = ảnh chung của Gym. Lỗi: 404 branch không thuộc Gym; 409 Gym chưa APPROVED.")
    @PostMapping
    public ResponseEntity<ApiResponse<GymMediaResponse>> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GymMediaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Media added",
                gymMediaService.add(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-016 — Xoá ảnh",
            description = "Actor: **Gym Operator**. Lỗi: 404 ảnh không thuộc Gym.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        gymMediaService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Media deleted", null));
    }

    @Operation(summary = "UC-016 — Danh sách ảnh của Gym",
            description = "Actor: **Gym Operator**. Lọc theo branchId nếu truyền.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GymMediaResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(gymMediaService.list(userDetails.getUsername(), branchId)));
    }
}
