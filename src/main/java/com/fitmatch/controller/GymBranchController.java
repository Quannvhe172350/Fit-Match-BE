package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.service.GymBranchService;
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
@RequestMapping("/api/gym/branches")
@RequiredArgsConstructor
@Tag(name = "G. Gym Branches", description = "Quản lý chi nhánh của Gym (UC-50 → UC-52)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymBranchController {

    private final GymBranchService branchService;

    @Operation(summary = "UC-50 — Tạo chi nhánh", description = "Actor: **Gym Operator** (Gym đã APPROVED). Lỗi: 409 chưa duyệt; 404 chưa có hồ sơ Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<BranchResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Branch created", branchService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-51 — Cập nhật chi nhánh", description = "Actor: **Gym Operator** (chủ sở hữu). Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Branch updated",
                branchService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-51 — Vô hiệu hoá chi nhánh", description = "Actor: **Gym Operator** (chủ sở hữu). Đặt active=false.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        branchService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Branch deactivated", null));
    }
}
