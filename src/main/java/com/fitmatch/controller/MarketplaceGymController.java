package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.dto.gym.TrainingPackageResponse;
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

    @Operation(
            summary = "UC-009 — Chi nhánh của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Chi nhánh đang hoạt động kèm giờ mở cửa theo ngày. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/branches")
    public ResponseEntity<ApiResponse<java.util.List<BranchResponse>>> branches(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymBranches(id)));
    }

    @Operation(
            summary = "UC-009 — Dịch vụ của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Chỉ dịch vụ PUBLISHED, kèm giá và booking rules (đặt cọc/hủy/min-notice) phục vụ quyết định đặt lịch. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/services")
    public ResponseEntity<ApiResponse<java.util.List<GymServiceResponse>>> services(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymServices(id)));
    }

    @Operation(
            summary = "UC-009 — Gói tập của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Chỉ gói PUBLISHED, kèm số buổi/hạn dùng/giá. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/packages")
    public ResponseEntity<ApiResponse<java.util.List<TrainingPackageResponse>>> packages(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymPackages(id)));
    }

    @Operation(
            summary = "UC-009 — Hình ảnh/media của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/media")
    public ResponseEntity<ApiResponse<java.util.List<GymMediaResponse>>> media(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymMedia(id)));
    }

    @Operation(
            summary = "UC-009 — PT của Gym (công khai)",
            description = "Actor: **Customer / Guest**. PT đang hoạt động của Gym — phục vụ chọn PT khi đặt lịch (UC-032). Phân trang. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/pts")
    public ResponseEntity<ApiResponse<PageResponse<PtPublicProfileResponse>>> pts(
            @PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymPts(id, pageable)));
    }
}
