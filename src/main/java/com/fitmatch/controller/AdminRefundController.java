package com.fitmatch.controller;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.AdminRefundCreateRequest;
import com.fitmatch.dto.payment.RefundDecisionRequest;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Refunds", description = "Finance/Admin xử lý hoàn tiền (UC-055/056). Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminRefundController {

    private final RefundService refundService;

    @Operation(
            summary = "UC-055 — Danh sách yêu cầu hoàn tiền",
            description = "Actor: **Admin/Finance**. Lọc theo trạng thái; bỏ trống = TẤT CẢ trạng thái.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> list(
            @Parameter(description = "Trạng thái; bỏ trống = tất cả")
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(refundService.listForAdmin(status, pageable)));
    }

    @Operation(
            summary = "UC-055 — Mở yêu cầu hoàn tiền thủ công",
            description = "Actor: **Admin/Finance**. Cho booking REJECTED/CANCELLED/NO_SHOW còn giữ tiền (gồm dữ liệu cũ bị kẹt trước khi có luồng refund). Yêu cầu luôn bao trùm toàn bộ phần đang giữ. Lỗi: 409 sai trạng thái hoặc đã có yêu cầu mở.")
    @PostMapping
    public ResponseEntity<ApiResponse<RefundResponse>> create(
            @Valid @RequestBody AdminRefundCreateRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Refund request created",
                refundService.createByAdmin(request.getBookingId(), request.getReason(),
                        actor.getUsername())));
    }

    @Operation(
            summary = "UC-056 — Duyệt và thực thi hoàn tiền",
            description = "Actor: **Admin/Finance**. approvedAmount bỏ trống = hoàn toàn bộ; duyệt một phần thì phần còn lại là phí Gym giữ (vào pending settlement). Bút toán ví ghi ngay; chuyển khoản thực tế cho khách thực hiện thủ công theo record này. Lỗi: 400 vượt số yêu cầu; 409 sai trạng thái.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RefundResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundDecisionRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Refund executed",
                refundService.approveAndExecute(id,
                        request != null ? request.getApprovedAmount() : null,
                        request != null ? request.getNote() : null,
                        actor.getUsername())));
    }

    @Operation(
            summary = "UC-056 — Từ chối yêu cầu hoàn tiền",
            description = "Actor: **Admin/Finance**. Tiền trở lại trạng thái HELD của booking. Lỗi: 409 sai trạng thái.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RefundResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundDecisionRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Refund rejected",
                refundService.reject(id, request != null ? request.getNote() : null,
                        actor.getUsername())));
    }
}
