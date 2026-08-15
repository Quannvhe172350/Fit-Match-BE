package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.gym.GymSearchCriteria;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.service.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
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
@org.springframework.validation.annotation.Validated
@RequiredArgsConstructor
@Tag(name = "C. Marketplace - Gym", description = "Tìm kiếm & xem hồ sơ Gym công khai (UC-18)")
@SecurityRequirements // public
public class MarketplaceGymController {

    private final MarketplaceService marketplaceService;

    @Operation(
            summary = "UC-18 — Tìm kiếm / lọc Gym (công khai)",
            description = """
                    Actor: **Customer / Guest**. Tìm Gym đã duyệt & hiển thị theo keyword (tên/mô tả), \
                    city, district và khoảng giá gói tập; phân trang. Read-only.

                    **Tìm theo bán kính (V55):** gửi kèm `lat` + `lng` (vị trí người dùng hoặc địa điểm \
                    tự chọn) và `radiusKm`. Khi có toạ độ, kết quả chỉ gồm Gym có trụ sở HOẶC chi nhánh \
                    đang hoạt động nằm trong bán kính, LUÔN sắp xếp theo khoảng cách tăng dần (tham số \
                    `sort` bị bỏ qua), và mỗi phần tử có thêm `distanceKm` + `latitude`/`longitude` của \
                    điểm gần nhất. Các bộ lọc keyword/city/district/giá vẫn áp dụng chồng lên.""")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GymPublicProfileResponse>>> search(
            @Parameter(description = "Từ khoá tên/mô tả") @RequestParam(required = false) String keyword,
            @Parameter(description = "Thành phố") @RequestParam(required = false) String city,
            @Parameter(description = "Quận/huyện") @RequestParam(required = false) String district,
            @Parameter(description = "Giá gói tập tối thiểu (VND)") @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "Giá gói tập tối đa (VND)") @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "Vĩ độ tâm tìm kiếm (-90..90); phải đi kèm lng")
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") java.math.BigDecimal lat,
            @Parameter(description = "Kinh độ tâm tìm kiếm (-180..180); phải đi kèm lat")
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") java.math.BigDecimal lng,
            @Parameter(description = "Bán kính (km); mặc định và trần lấy từ cấu hình app.geocoding")
            @RequestParam(required = false) @Positive Double radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        // Chỉ có một trong hai toạ độ là lỗi của client (thường do quên bind state
        // sau khi người dùng bỏ chọn vị trí) — báo 400 rõ ràng thay vì im lặng bỏ
        // qua bộ lọc vị trí rồi trả về gym ở đầu kia thành phố.
        if ((lat == null) != (lng == null)) {
            throw new com.fitmatch.exception.BusinessException(
                    com.fitmatch.common.enums.ErrorCode.VALIDATION_ERROR,
                    "lat and lng must be provided together");
        }
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.searchGyms(
                new GymSearchCriteria(keyword, city, district, minPrice, maxPrice, lat, lng, radiusKm),
                pageable)));
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

    // /{id}/services và /{id}/packages đã bỏ: vé bán theo CHI NHÁNH, xem
    // GET /api/marketplace/branches/{branchId}/ticket-types.

    @Operation(
            summary = "UC-009 — Hình ảnh/media của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/media")
    public ResponseEntity<ApiResponse<java.util.List<GymMediaResponse>>> media(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymMedia(id)));
    }

    @Operation(
            summary = "UC-047 — Cơ sở vật chất của Gym (công khai)",
            description = "Actor: **Customer / Guest**. Chỉ hạng mục đang hoạt động, kèm ảnh "
                    + "(media FACILITY/GALLERY). Lỗi: 404 gym không hiển thị.")
    @GetMapping("/{id}/facilities")
    public ResponseEntity<ApiResponse<java.util.List<com.fitmatch.dto.gym.FacilityResponse>>> facilities(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymFacilities(id)));
    }

    @Operation(
            summary = "UC-009 — PT của Gym (công khai)",
            description = """
                    Actor: **Customer / Guest**. PT đang hoạt động của Gym — phục vụ chọn PT khi \
                    đặt lịch (UC-032). Phân trang. Lỗi: 404 gym không hiển thị.

                    **branchId (bug S2-04):** truyền vào thì chỉ trả PT được phân công cho chi \
                    nhánh đó. Có PT chỉ phụ trách một chi nhánh; không lọc thì khách chọn nhầm và \
                    chỉ biết khi checkout báo "PT chưa được gán cho chi nhánh đã chọn".""")
    @GetMapping("/{id}/pts")
    public ResponseEntity<ApiResponse<PageResponse<PtPublicProfileResponse>>> pts(
            @PathVariable Long id,
            @Parameter(description = "Chỉ lấy PT phụ trách chi nhánh này")
            @RequestParam(required = false) Long branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.listGymPts(id, branchId, pageable)));
    }
}
