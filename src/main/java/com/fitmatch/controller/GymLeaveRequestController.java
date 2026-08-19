package com.fitmatch.controller;

import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymLeavePolicyDto;
import com.fitmatch.dto.gym.LeaveDecisionRequest;
import com.fitmatch.dto.gym.PtShiftAssignRequest;
import com.fitmatch.dto.gym.PtShiftAssignResponse;
import com.fitmatch.dto.pt.PtLeaveRequestResponse;
import com.fitmatch.service.PtLeaveRequestService;
import com.fitmatch.service.PtShiftRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

/**
 * Phía Gym của mô hình mới: xếp PT vào ca, duyệt đơn nghỉ, và cấu hình hạn mức
 * nghỉ hằng tháng.
 *
 * <p>Ba nhóm nằm chung một controller vì cùng một màn hình vận hành nhân sự và
 * cùng một quy tắc sở hữu (PT/đơn phải thuộc Gym đang đăng nhập).
 */
@RestController
@RequestMapping("/api/gym")
@RequiredArgsConstructor
@Tag(name = "G. Gym Workforce", description = "Xếp PT vào ca, duyệt đơn nghỉ, hạn mức nghỉ")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymLeaveRequestController {

    private final PtShiftRosterService rosterService;
    private final PtLeaveRequestService leaveRequestService;

    // ==================== Phân ca ====================

    @Operation(summary = "Xếp PT vào ca (lặp hoặc lẻ)",
            description = "Actor: **Gym Operator**. from != to + daysOfWeek = xếp LẶP; from == to = "
                    + "xếp LẺ một ngày. Idempotent — xếp lại không nhân đôi. Ngày chi nhánh đóng cửa "
                    + "và ngày PT đã có ca chồng giờ sẽ bị BỎ QUA và liệt kê trong response, không "
                    + "làm hỏng cả lượt xếp. "
                    + "Lỗi: 400 khoảng ngày quá 366 ngày hoặc ca không áp dụng thứ nào đã chọn; "
                    + "409 ca đang tắt hoặc PT chưa được gán vào chi nhánh của ca; "
                    + "404 PT/ca không thuộc Gym.")
    @PostMapping("/pts/{ptId}/shifts")
    public ResponseEntity<ApiResponse<PtShiftAssignResponse>> assignShift(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @Valid @RequestBody PtShiftAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Shifts assigned",
                rosterService.assign(userDetails.getUsername(), ptId, request)));
    }

    @Operation(summary = "Gỡ một ngày phân ca của PT",
            description = "Actor: **Gym Operator**. "
                    + "Lỗi: 409 ca đó đã có buổi tập được đặt (kèm danh sách buổi vướng — phải để "
                    + "khách đổi PT hoặc đổi giờ trước); 404 dòng phân ca không thuộc Gym.")
    @DeleteMapping("/pts/{ptId}/shifts/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> unassignShift(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @PathVariable Long assignmentId) {
        rosterService.unassign(userDetails.getUsername(), ptId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success("Shift assignment removed", null));
    }

    // ==================== Đơn nghỉ ====================

    @Operation(summary = "Danh sách đơn xin nghỉ của PT thuộc Gym",
            description = "Actor: **Gym Operator**. Lọc theo status nếu cần. Mới nhất trước.")
    @GetMapping("/leave-requests")
    public ResponseEntity<ApiResponse<PageResponse<PtLeaveRequestResponse>>> listLeaveRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LeaveStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.gymRequests(userDetails.getUsername(), status, pageable)));
    }

    @Operation(summary = "Số đơn đang chờ duyệt",
            description = "Actor: **Gym Operator**. Dùng cho badge trên menu.")
    @GetMapping("/leave-requests/pending-count")
    public ResponseEntity<ApiResponse<Long>> pendingCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.countPending(userDetails.getUsername())));
    }

    @Operation(summary = "Duyệt đơn xin nghỉ",
            description = "Actor: **Gym Operator**. Slot trong phạm vi đơn bị vô hiệu ngay. Buổi tập "
                    + "đã đặt nằm trong phạm vi KHÔNG bị huỷ: PT bị gỡ khỏi buổi, buổi giữ nguyên "
                    + "SCHEDULED (vé vẫn dùng được cả ngày), và KHÁCH được thông báo để chọn PT thay "
                    + "thế hoặc nhận hoàn phụ phí PT của ngày đó. "
                    + "Lỗi: 409 đơn không còn PENDING; 404 đơn không thuộc Gym.")
    @PostMapping("/leave-requests/{id}/approve")
    public ResponseEntity<ApiResponse<PtLeaveRequestResponse>> approve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request approved",
                leaveRequestService.approve(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Từ chối đơn xin nghỉ",
            description = "Actor: **Gym Operator**. Bắt buộc có lý do (5..1000 ký tự) — PT nhận được "
                    + "lý do để còn sắp xếp lại. Lịch ca của PT giữ nguyên. "
                    + "Lỗi: 400 thiếu lý do; 409 đơn không còn PENDING; 404 đơn không thuộc Gym.")
    @PostMapping("/leave-requests/{id}/reject")
    public ResponseEntity<ApiResponse<PtLeaveRequestResponse>> reject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody LeaveDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Leave request rejected",
                leaveRequestService.reject(userDetails.getUsername(), id, request.getReason())));
    }

    // ==================== Hạn mức nghỉ ====================

    @Operation(summary = "Xem hạn mức đơn nghỉ mỗi tháng",
            description = "Actor: **Gym Operator**. enabled=false = không giới hạn.")
    @GetMapping("/leave-policy")
    public ResponseEntity<ApiResponse<GymLeavePolicyDto>> getPolicy(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.getPolicy(userDetails.getUsername())));
    }

    @Operation(summary = "Cấu hình hạn mức đơn nghỉ mỗi tháng",
            description = "Actor: **Gym Operator**. Đếm theo tháng của fromDate; chỉ đơn PENDING và "
                    + "APPROVED tiêu lượt (đơn bị từ chối hoặc PT tự huỷ thì trả lại lượt). "
                    + "Lỗi: 400 bật hạn mức mà thiếu monthlyQuota.")
    @PutMapping("/leave-policy")
    public ResponseEntity<ApiResponse<GymLeavePolicyDto>> updatePolicy(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GymLeavePolicyDto request) {
        return ResponseEntity.ok(ApiResponse.success("Leave policy updated",
                leaveRequestService.updatePolicy(userDetails.getUsername(), request)));
    }
}
