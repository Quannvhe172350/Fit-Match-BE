package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.service.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/gyms")
@RequiredArgsConstructor
@Tag(name = "C. Marketplace - Gym", description = "Tìm kiếm & xem hồ sơ Gym công khai (UC-18)")
@SecurityRequirements // public
public class MarketplaceGymController {

    private final MarketplaceService marketplaceService;

    @Operation(
            summary = "UC-18 — Tìm kiếm / lọc Gym (công khai)",
            description = "Actor: **Customer / Guest**. Tìm Gym đã duyệt & hiển thị theo keyword (tên/mô tả) và city; phân trang. Read-only.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GymPublicProfileResponse>>> search(
            @Parameter(description = "Từ khoá tên/mô tả") @RequestParam(required = false) String keyword,
            @Parameter(description = "Thành phố") @RequestParam(required = false) String city,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.searchGyms(keyword, city, pageable)));
    }

    @Operation(
            summary = "UC-18 — Chi tiết Gym (công khai)",
            description = "Actor: **Customer / Guest**. Lỗi: 404 nếu không tồn tại / chưa được duyệt.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GymPublicProfileResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.getGymDetail(id)));
    }
}
