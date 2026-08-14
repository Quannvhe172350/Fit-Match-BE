package com.fitmatch.controller;

import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.dto.ticket.MarketplaceTicketTypeResponse;
import com.fitmatch.dto.ticket.TicketTypeResponse;
import com.fitmatch.service.GymServiceCatalogService;
import com.fitmatch.service.TicketTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Tag(name = "P. Marketplace Tickets", description = "Vé đang bán tại một chi nhánh")
public class MarketplaceTicketTypeController {

    private final TicketTypeService ticketTypeService;
    private final GymServiceCatalogService gymServiceCatalogService;

    @Operation(summary = "Vé đang bán tại một chi nhánh",
            description = "Công khai (GET /api/marketplace/** permitAll). Chỉ trả vé PUBLISHED. "
                    + "priceWithPt đã tính sẵn = price + ptSurchargePerDay * dayCount.")
    @GetMapping("/branches/{branchId}/ticket-types")
    public ResponseEntity<ApiResponse<List<TicketTypeResponse>>> listForBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(ticketTypeService.listForBranch(branchId)));
    }

    @Operation(summary = "Dịch vụ kèm vé bán tại một chi nhánh (V82)",
            description = "Công khai. Add-on khách có thể tick thêm lúc mua vé; giá cộng MỘT LẦN "
                    + "cho cả vé, không nhân theo số ngày. Chỉ trả dịch vụ PUBLISHED.")
    @GetMapping("/branches/{branchId}/services")
    public ResponseEntity<ApiResponse<List<GymServiceResponse>>> servicesForBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(gymServiceCatalogService.listForBranch(branchId)));
    }

    @Operation(summary = "Duyệt vé đang bán trên toàn sàn",
            description = "Công khai. Dùng cho trang duyệt vé gói (kind=PACKAGE) và vé ngày "
                    + "(kind=DAY); bỏ trống kind để lấy cả hai. Lọc theo khu vực của phòng gym "
                    + "và theo khoảng giá NIÊM YẾT (chưa cộng phụ phí PT). Mỗi item kèm gym và "
                    + "danh sách chi nhánh bán vé — FE cần branchId để dựng liên kết mua vé.")
    @GetMapping("/ticket-types")
    public ResponseEntity<ApiResponse<PageResponse<MarketplaceTicketTypeResponse>>> search(
            @Parameter(description = "DAY | PACKAGE; bỏ trống = cả hai")
            @RequestParam(required = false) TicketKind kind,
            @Parameter(description = "Khớp tên vé hoặc tên phòng gym")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                ticketTypeService.searchPublic(kind, keyword, city, district,
                        minPrice, maxPrice, pageable))));
    }
}
