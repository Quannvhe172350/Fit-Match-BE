package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtLeaveRequestCreate;
import com.fitmatch.dto.pt.PtLeaveRequestResponse;
import com.fitmatch.dto.pt.PtShiftDto;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.service.PtLeaveRequestService;
import com.fitmatch.service.PtSessionService;
import com.fitmatch.service.PtShiftRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
 * Lịch của PT trong mô hình mới. Thay {@code PtDailyAvailabilityController}:
 * {@code GET/PUT /api/pt/availability/daily} đã bị XOÁ HẲN — PT không còn khai
 * lịch rảnh, Gym xếp ca (V85/V86) và PT chỉ xem cùng gửi đơn nghỉ (V87).
 *
 * <p>Xoá hẳn thay vì trả 409 với thông điệp "lịch do Gym xếp": FE nằm cùng repo
 * nên sửa được ngay trong cùng đợt, và giữ một endpoint chỉ để ném lỗi chính là
 * kiểu điểm chết mà đợt này đang dọn.
 */
@RestController
@RequestMapping("/api/pt")
@RequiredArgsConstructor
@Tag(name = "T. PT Schedule", description = "Lịch ca được xếp, lịch dạy và đơn xin nghỉ của PT")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PT')")
public class PtShiftController {

    private final PtShiftRosterService rosterService;
    private final PtLeaveRequestService leaveRequestService;
    private final PtSessionService ptSessionService;

    @Operation(summary = "Lịch ca của tôi",
            description = "Actor: **PT**. READ-ONLY — ca do Gym xếp, PT không sửa được. "
                    + "onLeave=true là ca đã được duyệt nghỉ. Lỗi: 400 khoảng ngày quá 366 ngày.")
    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<List<PtShiftDto>>> myShifts(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                rosterService.myShifts(userDetails.getUsername(), from, to)));
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

    @Operation(summary = "Đơn xin nghỉ của tôi", description = "Actor: **PT**. Mới nhất trước.")
    @GetMapping("/leave-requests")
    public ResponseEntity<ApiResponse<PageResponse<PtLeaveRequestResponse>>> myLeaveRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.myRequests(userDetails.getUsername(), pageable)));
    }

    @Operation(summary = "Gửi đơn xin nghỉ / báo bận",
            description = "Actor: **PT**. Đơn ở trạng thái PENDING cho tới khi Gym duyệt — báo bận "
                    + "KHÔNG tự khoá slot. scope=SHIFT phải kèm shiftIds; scope=TIME_RANGE phải kèm "
                    + "startTime/endTime. "
                    + "Lỗi: 400 dữ liệu không hợp lệ; 409 chồng đơn đang hiệu lực, vượt hạn mức nghỉ "
                    + "trong tháng do Gym cấu hình, hoặc đơn đè lên buổi đã đặt mà không đủ số giờ "
                    + "báo trước (Admin cấu hình pt.leave.min-lead-hours).")
    @PostMapping("/leave-requests")
    public ResponseEntity<ApiResponse<PtLeaveRequestResponse>> submitLeave(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PtLeaveRequestCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Leave request submitted",
                leaveRequestService.submit(userDetails.getUsername(), request)));
    }

    @Operation(summary = "Huỷ đơn xin nghỉ",
            description = "Actor: **PT** (chủ đơn). Chỉ huỷ được đơn còn PENDING. "
                    + "Lỗi: 404 đơn không thuộc bạn; 409 đơn đã được duyệt hoặc từ chối.")
    @PostMapping("/leave-requests/{id}/cancel")
    public ResponseEntity<ApiResponse<PtLeaveRequestResponse>> cancelLeave(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request cancelled",
                leaveRequestService.cancel(userDetails.getUsername(), id)));
    }
}
