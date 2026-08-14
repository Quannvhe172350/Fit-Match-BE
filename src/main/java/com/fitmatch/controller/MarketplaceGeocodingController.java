package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.config.GeocodingProperties;
import com.fitmatch.dto.gym.GeocodeResponse;
import com.fitmatch.dto.gym.PlaceSuggestionResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.service.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Proxy geocoding cho FE (UC-18).
 *
 * <p>Từ V65, FE không còn khoá geocode phía trình duyệt nào (bản đồ chạy Leaflet
 * + tile OpenStreetMap, vốn không cần khoá), nên đây là đường DUY NHẤT để ô "tìm
 * quanh đây" hoạt động — vì vậy {@code app.geocoding.public-geocode-enabled}
 * mặc định bật.
 *
 * <p>Vẫn là bề mặt lạm dụng: endpoint công khai tiêu hạn mức của nền tảng, mà
 * hạn mức gói miễn phí tính theo NGÀY. {@code AuthRateLimitFilter} giới hạn theo
 * IP; tắt cấu hình trên là chặn hẳn.
 */
@RestController
@RequestMapping("/api/marketplace/geocode")
@Validated
@RequiredArgsConstructor
@Tag(name = "C. Marketplace - Geocoding", description = "Ánh xạ địa chỉ <-> toạ độ cho tìm kiếm theo bán kính (UC-18)")
@SecurityRequirements // public
public class MarketplaceGeocodingController {

    private final GeocodingService geocodingService;
    private final GeocodingProperties properties;

    @Operation(summary = "UC-18 — Địa chỉ -> toạ độ",
            description = "Actor: **Customer / Guest**. Phục vụ ô \"tìm theo địa điểm tự chọn\" khi FE "
                    + "không có key Maps JavaScript. Lỗi: 404 không tìm thấy địa điểm, "
                    + "409 khi tính năng chưa được bật, 429 khi vượt rate limit.")
    @GetMapping
    public ResponseEntity<ApiResponse<GeocodeResponse>> geocode(
            @Parameter(description = "Địa chỉ / tên địa điểm cần tra toạ độ")
            @RequestParam @NotBlank String address) {
        requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(geocodingService.geocode(address)
                .map(GeocodeResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Location", address))));
    }

    @Operation(summary = "UC-18 — Toạ độ -> địa chỉ",
            description = "Actor: **Customer / Guest / Gym owner**. Đổi toạ độ thành địa chỉ đọc được — "
                    + "nút \"Vị trí của tôi\" ở marketplace, và ghim tay trên bản đồ ở form địa chỉ gym "
                    + "(trả kèm quận/huyện + tỉnh/thành để form điền thẳng vào hai ô riêng). "
                    + "Lỗi: 404 không có địa chỉ khớp, 409 khi tính năng chưa được bật.")
    @GetMapping("/reverse")
    public ResponseEntity<ApiResponse<GeocodeResponse>> reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng) {
        requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(geocodingService.reverseGeocode(lat, lng)
                .map(GeocodeResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Location", lat + "," + lng))));
    }

    @Operation(summary = "UC-18 — Gợi ý địa điểm khi đang gõ",
            description = "Actor: **Customer / Guest / Gym owner**. Thay Places Autocomplete phía trình "
                    + "duyệt — khoá API nằm lại ở server. Trả mảng RỖNG khi nhà cung cấp hiện tại không "
                    + "hỗ trợ gợi ý; khi đó FE lui về chế độ nhấn Enter để tra cả chuỗi. "
                    + "Lỗi: 409 khi tính năng chưa được bật, 429 khi vượt rate limit.")
    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<PlaceSuggestionResponse>>> autocomplete(
            @Parameter(description = "Chuỗi người dùng đang gõ")
            @RequestParam("q") @NotBlank String query,
            @Parameter(description = "Số gợi ý tối đa (1..10)")
            @RequestParam(required = false, defaultValue = "5") @Min(1) @Max(10) int limit) {
        requireEnabled();
        // Danh sách rỗng là kết quả hợp lệ ("không có gợi ý"), KHÔNG phải 404: ô nhập
        // vẫn dùng được, người dùng gõ tiếp hoặc nhấn Enter để tra cả chuỗi.
        return ResponseEntity.ok(ApiResponse.success(geocodingService.autocomplete(query, limit)
                .stream()
                .map(PlaceSuggestionResponse::of)
                .toList()));
    }

    private void requireEnabled() {
        if (!properties.isPublicGeocodeEnabled() || !geocodingService.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Server-side geocoding is not enabled. Configure app.geocoding.provider with its "
                            + "API key, and app.geocoding.public-geocode-enabled.");
        }
    }
}
