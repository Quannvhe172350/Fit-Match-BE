package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.service.GeocodingBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/geocoding")
@Validated
@RequiredArgsConstructor
@Tag(name = "H. Admin - Geocoding",
        description = "Bổ sung toạ độ cho gym/chi nhánh tạo trước V55 (UC-18). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGeocodingController {

    private final GeocodingBackfillService geocodingBackfillService;

    @Operation(summary = "UC-18 — Tình trạng phủ toạ độ",
            description = "Actor: **Admin**. Bao nhiêu gym/chi nhánh đã có toạ độ, bao nhiêu địa chỉ chỉ "
                    + "khớp ở mức tương đối, và số câu trả lời đang được đệm. Dùng để quyết định có cần "
                    + "chạy backfill hay không.")
    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<GeocodingBackfillService.GeocodingCoverage>> coverage() {
        return ResponseEntity.ok(ApiResponse.success(geocodingBackfillService.coverage()));
    }

    @Operation(summary = "UC-18 — Backfill toạ độ",
            description = "Actor: **Admin**. Geocode các hồ sơ gym / chi nhánh còn thiếu lat-lng để chúng "
                    + "xuất hiện trong tìm kiếm theo bán kính. Chạy lặp lại cho tới khi `remaining` = 0. "
                    + "Lỗi: 409 khi chưa cấu hình app.google-maps.api-key.")
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<GeocodingBackfillService.BackfillResult>> backfill(
            @Parameter(description = "Số bản ghi xử lý tối đa mỗi lần gọi (mỗi bản ghi = 1 lượt gọi Google)")
            @RequestParam(defaultValue = "50") @Positive @Max(500) int limit,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Geocoding backfill executed",
                geocodingBackfillService.backfill(limit, actor.getUsername())));
    }
}
