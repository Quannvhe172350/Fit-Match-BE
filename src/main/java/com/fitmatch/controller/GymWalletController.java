package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WalletResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.service.WalletQueryService;
import com.fitmatch.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym/wallet")
@RequiredArgsConstructor
@Tag(name = "F. Gym - Wallet", description = "Ví Gym: số dư, sổ cái, rút tiền (UC-061/062). Yêu cầu ROLE_GYM_OPERATOR + Gym APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymWalletController {

    private final WalletQueryService walletQueryService;
    private final WithdrawalService withdrawalService;

    @Operation(
            summary = "UC-061 — Xem số dư ví",
            description = "Actor: **Gym Operator**. 4 bucket: held (escrow) / pending (chờ hết holding period) / available (rút được) / frozen (dispute hoặc rút tiền đang xử lý). Lỗi: 409 Gym chưa APPROVED; 404 chưa có hồ sơ Gym.")
    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                walletQueryService.getForGym(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-061 — Lịch sử bút toán ví",
            description = "Actor: **Gym Operator**. Sổ cái append-only, mới nhất trước; mỗi bút toán kèm snapshot 4 bucket sau khi áp.")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> transactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                walletQueryService.transactionsForGym(userDetails.getUsername(), pageable)));
    }

    @Operation(
            summary = "UC-062 — Gửi yêu cầu rút tiền",
            description = "Actor: **Gym Operator**. Số tiền được giữ chỗ ngay (available -> frozen) chờ Finance duyệt; từ chối sẽ trả lại available. Lỗi: 409 số dư khả dụng không đủ.")
    @PostMapping("/withdrawals")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WithdrawalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Withdrawal requested",
                withdrawalService.create(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-062 — Danh sách yêu cầu rút tiền của Gym",
            description = "Actor: **Gym Operator**.")
    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> myWithdrawals(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                withdrawalService.listForGym(userDetails.getUsername(), pageable)));
    }
}
