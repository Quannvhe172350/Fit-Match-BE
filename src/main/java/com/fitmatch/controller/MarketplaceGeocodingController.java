package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.config.GoogleMapsProperties;
import com.fitmatch.dto.gym.GeocodeResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.service.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Proxy geocoding cho FE (UC-18) — dùng khi FE KHÔNG có key Maps JavaScript riêng
 * và vì thế không tự geocode phía trình duyệt được.
 *
 * <p>Mặc định TẮT ({@code app.google-maps.public-geocode-enabled=false}): endpoint
 * công khai gọi Google bằng key của nền tảng là bề mặt lạm bị đốt quota. Khi bật,
 * {@code AuthRateLimitFilter} giới hạn theo IP.
 */
@RestController
@RequestMapping("/api/marketplace/geocode")
@Validated
@RequiredArgsConstructor
@Tag(name = "C. Marketplace - Geocoding", description = "Ánh xạ địa chỉ <-> toạ độ cho tìm kiếm theo bán kính (UC-18)")
@SecurityRequirements // public
public class MarketplaceGeocodingController {

    private final GeocodingService geocodingService;
    private final GoogleMapsProperties properties;

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
            description = "Actor: **Customer / Guest**. Đổi toạ độ GPS của nút \"Vị trí của tôi\" thành "
                    + "địa chỉ đọc được. Lỗi: 404 không có địa chỉ khớp, 409 khi tính năng chưa được bật.")
    @GetMapping("/reverse")
    public ResponseEntity<ApiResponse<GeocodeResponse>> reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng) {
        requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(geocodingService.reverseGeocode(lat, lng)
                .map(GeocodeResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Location", lat + "," + lng))));
    }

    private void requireEnabled() {
        if (!properties.isPublicGeocodeEnabled() || !geocodingService.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Server-side geocoding is not enabled. Configure app.google-maps.api-key and "
                            + "app.google-maps.public-geocode-enabled, or use a browser Maps JavaScript key.");
        }
    }
}
