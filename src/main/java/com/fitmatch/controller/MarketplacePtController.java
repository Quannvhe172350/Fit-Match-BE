package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
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
@RequestMapping("/api/marketplace/pts")
@RequiredArgsConstructor
@Tag(name = "C. Marketplace - PT", description = "Tìm kiếm & xem hồ sơ PT công khai (UC-14)")
@SecurityRequirements // public — không yêu cầu auth
public class MarketplacePtController {

    private final MarketplaceService marketplaceService;

    @Operation(
            summary = "UC-14 — Tìm kiếm / lọc PT (công khai)",
            description = """
                    Actor: **Customer / Guest**. Tìm PT đã được duyệt & đang hiển thị, theo keyword (tên/chuyên môn/bio),
                    specialization, serviceArea; có phân trang. Read-only, không cần đăng nhập.
                    """)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PtPublicProfileResponse>>> search(
            @Parameter(description = "Từ khoá tên/chuyên môn/bio") @RequestParam(required = false) String keyword,
            @Parameter(description = "Chuyên môn") @RequestParam(required = false) String specialization,
            @Parameter(description = "Khu vực phục vụ") @RequestParam(required = false) String serviceArea,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.searchPts(keyword, specialization, serviceArea, pageable)));
    }

    @Operation(
            summary = "UC-14 — Chi tiết PT (công khai)",
            description = "Actor: **Customer / Guest**. Xem hồ sơ PT công khai kèm chứng chỉ. Lỗi: 404 nếu không tồn tại / chưa được duyệt.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PtPublicProfileResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.getPtDetail(id)));
    }
}
