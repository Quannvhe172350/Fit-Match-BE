package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.SessionNoteRequest;
import com.fitmatch.dto.booking.SessionNoteResponse;
import com.fitmatch.service.BookingQueryService;
import com.fitmatch.service.PtBookingService;
import com.fitmatch.service.SessionNoteService;
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
    private final PtBookingService ptBookingService;
    private final SessionNoteService sessionNoteService;

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

    @Operation(
            summary = "UC-046 — PT check-in cho khách",
            description = "Actor: **PT**. Ghi nhận khách đến cho buổi tập mình phụ trách. Booking CONFIRMED, trong cửa sổ 30' trước giờ bắt đầu đến hết giờ. Lỗi: 409 sai trạng thái/ngoài cửa sổ; 404 không phải buổi của bạn.")
    @org.springframework.web.bind.annotation.PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<BookingResponse>> checkIn(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Checked in",
                ptBookingService.checkIn(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-048 — PT ghi chú buổi tập (kèm bằng chứng)",
            description = "Actor: **PT**. Ghi tiến độ/bằng chứng cho buổi tập mình phụ trách; làm căn cứ theo dõi luyện tập (UC-051) và tranh chấp.")
    @org.springframework.web.bind.annotation.PostMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<SessionNoteResponse>> addNote(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody SessionNoteRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Note added",
                        sessionNoteService.addForPt(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-048 — Danh sách ghi chú buổi tập",
            description = "Actor: **PT**.")
    @GetMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<java.util.List<SessionNoteResponse>>> notes(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                sessionNoteService.listForPt(userDetails.getUsername(), id)));
    }
}
