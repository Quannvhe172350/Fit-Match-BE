package com.fitmatch.controller;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.DecisionNoteRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.service.WithdrawalService;
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
@RequestMapping("/api/admin/withdrawals")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Withdrawals", description = "Finance/Admin duyệt và chi trả yêu cầu rút tiền của Gym, PT và khách hàng (UC-062). Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    @Operation(
            summary = "UC-062 — Danh sách yêu cầu rút tiền",
            description = "Actor: **Admin/Finance**. Không truyền bộ lọc = lấy tất cả. `ownerType` lọc theo loại chủ ví (GYM / PT / CUSTOMER). Mỗi bản ghi kèm số tài khoản, tên ngân hàng, tên người thụ hưởng và (khi đã duyệt) link QR để quét chuyển khoản.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> list(
            @Parameter(description = "Trạng thái; bỏ trống = không lọc")
            @RequestParam(required = false) WithdrawalStatus status,
            @Parameter(description = "Loại chủ ví: GYM / PT / CUSTOMER; bỏ trống = không lọc")
            @RequestParam(required = false) WalletOwnerType ownerType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                withdrawalService.listForAdmin(status, ownerType, pageable)));
    }

    @Operation(
            summary = "UC-062 — Duyệt yêu cầu rút tiền",
            description = "Actor: **Admin/Finance**. PENDING -> APPROVED và sinh mã QR VietQR (`qrContent`) trỏ tới tài khoản người thụ hưởng, nội dung chuyển khoản đã nhúng sẵn `refCode`. Quét QR chuyển tiền xong, webhook Casso sẽ tự chuyển lệnh sang PAID khi ngân hàng báo trừ tiền — không cần bấm mark-paid nếu số tiền và nội dung khớp. Lỗi: 409 sai trạng thái.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal approved",
                withdrawalService.approve(id, request != null ? request.getNote() : null,
                        actor.getUsername())));
    }

    @Operation(
            summary = "UC-062 — Từ chối yêu cầu rút tiền",
            description = "Actor: **Admin/Finance**. PENDING -> REJECTED; tiền giữ chỗ trả lại available của chủ ví. Lỗi: 409 sai trạng thái.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal rejected",
                withdrawalService.reject(id, request != null ? request.getNote() : null,
                        actor.getUsername())));
    }

    @Operation(
            summary = "UC-062 — Xác nhận đã chi trả",
            description = "Actor: **Admin/Finance**. APPROVED -> PAID sau khi đã chuyển khoản thủ công; tiền rời nền tảng. Chỉ cần dùng khi webhook Casso không tự khớp được (chuyển khoản sai nội dung, ngoài giờ đồng bộ, chuyển từ tài khoản khác). D-11: bắt buộc `payoutReference` (mã giao dịch chuyển khoản) để đối soát sao kê. Lỗi: 400 thiếu payoutReference; 409 sai trạng thái.")
    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> markPaid(
            @PathVariable Long id,
            @Valid @RequestBody com.fitmatch.dto.payment.MarkPaidRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal paid",
                withdrawalService.markPaid(id, request.getPayoutReference(), request.getNote(),
                        actor.getUsername())));
    }
}
