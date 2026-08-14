package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.ConfirmPtSessionRequest;
import com.fitmatch.dto.ticket.GymCalendarDayResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.service.GymTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Phía Gym trong mô hình vé. Cố ý KHÔNG có accept / reject / cancel /
 * assign-pt / reschedule: gym không duyệt và không can thiệp lịch của khách
 * nữa (quyết định #7). Thao tác duy nhất là xác nhận buổi có PT kèm ảnh.
 */
@RestController
@RequestMapping("/api/gym")
@RequiredArgsConstructor
@Tag(name = "G. Gym Calendar", description = "Lịch tập và vé đã bán của Gym")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymCalendarController {

    private final GymTicketService gymTicketService;

    @Operation(summary = "Lịch tập của một chi nhánh, gom sẵn theo ngày",
            description = "Actor: **Gym Operator**. Hỏi ĐÚNG khoảng ngày đang xem (tối đa 92 ngày) — "
                    + "FE không tải cả trang 200 bản ghi rồi tự gom nữa. Read-only.")
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<GymCalendarDayResponse>>> calendar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                gymTicketService.calendar(userDetails.getUsername(), branchId, from, to)));
    }

    @Operation(summary = "Vé đã bán",
            description = "Actor: **Gym Operator**. Lọc theo chi nhánh và khoảng ngày MUA.")
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> tickets(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                gymTicketService.tickets(userDetails.getUsername(), branchId, from, to, pageable)));
    }

    @Operation(summary = "Xác nhận buổi tập có PT kèm ảnh",
            description = "Actor: **Gym Operator**. Chỉ ghi nhận bằng chứng — KHÔNG chặn tiền và "
                    + "không đổi trạng thái buổi. Lỗi: 409 buổi không có PT / đã xác nhận.")
    @PostMapping("/sessions/{id}/confirm-pt")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> confirmPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmPtSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT attendance confirmed",
                gymTicketService.confirmPtSession(userDetails.getUsername(), id, request)));
    }
}
