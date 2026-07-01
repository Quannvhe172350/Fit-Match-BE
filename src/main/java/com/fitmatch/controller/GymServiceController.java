package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.service.GymServiceCatalogService;
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
@RequestMapping("/api/gym/services")
@RequiredArgsConstructor
@Tag(name = "G. Gym Services", description = "Quản lý dịch vụ của Gym (UC-53 → UC-55)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymServiceController {

    private final GymServiceCatalogService catalogService;

    @Operation(summary = "UC-53 — Tạo dịch vụ Gym", description = "Actor: **Gym Operator** (Gym đã APPROVED). Lỗi: 409 chưa duyệt; 404 chưa có hồ sơ Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<GymServiceResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GymServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gym service created", catalogService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-54 — Cập nhật dịch vụ Gym", description = "Actor: **Gym Operator** (chủ sở hữu). Lỗi: 404 không tồn tại/không thuộc về bạn.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GymServiceResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody GymServiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gym service updated",
                catalogService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "UC-54 — Vô hiệu hoá dịch vụ Gym", description = "Actor: **Gym Operator** (chủ sở hữu). Đặt active=false.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        catalogService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Gym service deactivated", null));
    }
}
