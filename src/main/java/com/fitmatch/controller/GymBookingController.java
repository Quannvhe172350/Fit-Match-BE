package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.RejectRequest;
import com.fitmatch.dto.booking.AssignBookingPtRequest;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.GymAcceptBookingRequest;
import com.fitmatch.service.GymBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym/bookings")
@RequiredArgsConstructor
@Tag(name = "E. Gym - Bookings", description = "Gym xử lý booking (UC-037..039, 041..043). Yêu cầu ROLE_GYM_OPERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymBookingController {

    private final GymBookingService gymBookingService;

    @Operation(
            summary = "UC-037 — Hộp thư booking của Gym",
            description = "Actor: **Gym Operator**. Danh sách booking của Gym theo trạng thái (mặc định PENDING_GYM chờ xử lý), phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Trạng thái (mặc định PENDING_GYM)")
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                gymBookingService.list(userDetails.getUsername(), status, pageable)));
    }

    @Operation(
            summary = "UC-038 — Nhận booking",
            description = "Actor: **Gym Operator**. PENDING_GYM -> CONFIRMED; truyền ptId để gán PT phụ trách (UC-039), PT khách đề xuất được xác nhận lại. Lỗi: 409 sai trạng thái hoặc PT không nhận được lịch; 404 không thuộc Gym.")
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<BookingResponse>> accept(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) GymAcceptBookingRequest request) {
        Long ptId = request != null ? request.getPtId() : null;
        return ResponseEntity.ok(ApiResponse.success("Booking accepted",
                gymBookingService.accept(userDetails.getUsername(), id, ptId)));
    }

    @Operation(
            summary = "UC-038 — Từ chối booking",
            description = "Actor: **Gym Operator**. PENDING_GYM -> REJECTED kèm lý do; booking đã giữ tiền sẽ được hoàn ở phase payment. Lỗi: 409 sai trạng thái; 404 không thuộc Gym.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BookingResponse>> reject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking rejected",
                gymBookingService.reject(userDetails.getUsername(), id, request.getReason())));
    }

    @Operation(
            summary = "UC-039 — Gán/đổi PT phụ trách booking",
            description = "Actor: **Gym Operator**. Khi PENDING_GYM/CONFIRMED; PT phải thuộc Gym, được gán đúng đích và rảnh khung giờ. Lỗi: 409 vi phạm; 404 PT/booking không thuộc Gym.")
    @PostMapping("/{id}/assign-pt")
    public ResponseEntity<ApiResponse<BookingResponse>> assignPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AssignBookingPtRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT assigned",
                gymBookingService.assignPt(userDetails.getUsername(), id, request.getPtId())));
    }
}
