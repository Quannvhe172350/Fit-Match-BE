package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.PtAvailabilityRequest;
import com.fitmatch.dto.pt.PtAvailabilitySaveResponse;
import com.fitmatch.dto.pt.PtAvailabilitySlotDto;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.service.PtDailyAvailabilityService;
import com.fitmatch.service.PtSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Lịch của PT trong mô hình vé.
 *
 * <p>Đường dẫn cố ý KHÔNG dùng {@code /api/pt/availability}: chỗ đó đang là lịch
 * lặp theo thứ của mô hình cũ và hai luồng phải sống chung tới hết P2. P4 xoá
 * endpoint cũ, đường dẫn này giữ nguyên để FE không phải sửa lại lần nữa.
 */
@RestController
@RequestMapping("/api/pt")
@RequiredArgsConstructor
@Tag(name = "T. PT Daily Schedule", description = "Lịch rảnh theo ngày và lịch dạy của PT")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PT')")
public class PtDailyAvailabilityController {

    private final PtDailyAvailabilityService availabilityService;
    private final PtSessionService ptSessionService;

    @Operation(summary = "Khung giờ đã khai theo ngày", description = "Actor: **PT**.")
    @GetMapping("/availability/daily")
    public ResponseEntity<ApiResponse<List<PtAvailabilitySlotDto>>> mySlots(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.mySlots(userDetails.getUsername(), from, to)));
    }

    @Operation(summary = "Khai lịch rảnh cho một khoảng ngày",
            description = "Actor: **PT**. Thay TRỌN khoảng [from, to] bằng danh sách gửi lên; "
                    + "gửi slots rỗng = nghỉ cả khoảng. LUÔN lưu thành công — khai dưới 20 ngày chỉ "
                    + "trả warning và không ảnh hưởng việc khách đặt. "
                    + "Lỗi: 409 khi bỏ khung giờ đã có khách đặt.")
    @PutMapping("/availability/daily")
    public ResponseEntity<ApiResponse<PtAvailabilitySaveResponse>> save(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PtAvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Availability saved",
                availabilityService.save(userDetails.getUsername(), request)));
    }

    @Operation(summary = "Lịch dạy của tôi", description = "Actor: **PT**. Read-only.")
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<TrainingSessionResponse>>> mySessions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                ptSessionService.mySessions(userDetails.getUsername(), from, to)));
    }
}
