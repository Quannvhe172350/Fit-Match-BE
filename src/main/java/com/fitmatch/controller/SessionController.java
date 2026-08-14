package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.dto.ticket.UpdateSessionDateRequest;
import com.fitmatch.dto.ticket.UpdateSessionPtRequest;
import com.fitmatch.service.TicketSchedulingService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "C. Training Sessions", description = "Ngày tập của khách")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class SessionController {

    private final TicketSchedulingService schedulingService;
    private final com.fitmatch.service.TicketReviewService reviewService;

    @Operation(summary = "Lịch tập của tôi trong một khoảng ngày",
            description = "Actor: **Customer**. Lọc khoảng ngày ở server. FE dùng cho lịch cá nhân "
                    + "và cho cảnh báo 'ngày này bạn đã có buổi tập ở phòng gym khác'.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TrainingSessionResponse>>> mySessions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                schedulingService.mySessions(userDetails.getUsername(), from, to)));
    }

    @Operation(summary = "Dời ngày tập",
            description = "Actor: **Customer** (chủ vé). CHỈ vé DAY — vé gói gọi vào nhận 409. "
                    + "Hạn đổi là 00:00 của ngày tập.")
    @PutMapping("/{id}/date")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> updateDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionDateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session date updated",
                schedulingService.updateDate(userDetails.getUsername(), id, request.getDate())));
    }

    @Operation(summary = "Chọn / đổi PT cho một ngày",
            description = "Actor: **Customer** (chủ vé). Dùng cho cả đổi khung giờ trong cùng ngày "
                    + "lẫn bổ sung PT cho ngày đang trống của vé gói. Yêu cầu vé có kèm PT.")
    @PutMapping("/{id}/pt")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> setPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionPtRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT updated",
                schedulingService.setPt(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Bỏ PT khỏi một ngày",
            description = "Actor: **Customer** (chủ vé). Không hoàn tiền — phụ phí PT tính theo vé.")
    @DeleteMapping("/{id}/pt")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> removePt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PT removed",
                schedulingService.removePt(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Đánh giá PT của một buổi tập",
            description = "Actor: **Customer** (chủ vé). Mở ngay khi buổi đó xong (DONE) và buổi "
                    + "có PT — câu 36. Mỗi buổi đúng một đánh giá. Đánh giá PHÒNG GYM nằm ở "
                    + "POST /api/tickets/{id}/review và chỉ mở khi dùng hết vé.")
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<com.fitmatch.dto.review.ReviewResponse>> reviewPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody com.fitmatch.dto.review.TicketReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review created",
                reviewService.reviewPt(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Check-in buổi tập có PT",
            description = "Actor: **Customer** (chủ vé). Chỉ buổi CÓ PT và chỉ trong đúng ngày tập. "
                    + "Không đổi trạng thái buổi và không ảnh hưởng dòng tiền — buổi vẫn tiêu theo ngày.")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> checkIn(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Checked in",
                schedulingService.checkIn(userDetails.getUsername(), id)));
    }
}
