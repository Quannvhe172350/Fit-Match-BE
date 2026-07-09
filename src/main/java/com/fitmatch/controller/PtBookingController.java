package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.service.BookingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pt/bookings")
@RequiredArgsConstructor
@Tag(name = "E. PT - Bookings", description = "Lịch buổi tập được gán cho PT (UC-045). Yêu cầu ROLE_PT.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PT')")
public class PtBookingController {

    private final BookingQueryService bookingQueryService;

    @Operation(
            summary = "UC-045 — Lịch buổi tập của PT",
            description = "Actor: **PT**. Danh sách booking được gán cho mình, lọc theo trạng thái, phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> mySchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.ptSchedule(userDetails.getUsername(), status, pageable)));
    }
}
