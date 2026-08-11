package com.fitmatch.controller;

import com.fitmatch.common.enums.WalletOwnerType;
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

/**
 * Ví khách hàng (V61). Tiền vào ví là các khoản hoàn từ refund/tranh chấp —
 * trước đây khoản này chỉ trừ khỏi held của gym rồi phải chuyển khoản tay.
 */
@RestController
@RequestMapping("/api/customer/wallet")
@RequiredArgsConstructor
@Tag(name = "D. Customer - Wallet", description = "Ví khách hàng: số dư tiền hoàn, sổ cái, rút tiền (UC-061/062).")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class CustomerWalletController {

    private final WalletQueryService walletQueryService;
    private final WithdrawalService withdrawalService;

    @Operation(
            summary = "UC-061 — Xem số dư ví khách hàng",
            description = "Actor: **Customer**. available = tiền hoàn đã về, rút được bất cứ lúc nào; frozen = đang có lệnh rút chờ xử lý. Ví được tạo tự động ở lần xem đầu tiên.")
    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                walletQueryService.getForOwner(userDetails.getUsername(), WalletOwnerType.CUSTOMER)));
    }

    @Operation(
            summary = "UC-061 — Lịch sử bút toán ví khách hàng",
            description = "Actor: **Customer**. REFUND_CREDIT = tiền hoàn vào ví; WITHDRAWAL = đã rút về ngân hàng.")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> transactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                walletQueryService.transactionsForOwner(
                        userDetails.getUsername(), WalletOwnerType.CUSTOMER, pageable)));
    }

    @Operation(
            summary = "UC-062 — Khách hàng gửi yêu cầu rút tiền hoàn",
            description = "Actor: **Customer**. Chọn tài khoản thụ hưởng đã lưu (`bankAccountId`). Lỗi: 409 số dư khả dụng không đủ.")
    @PostMapping("/withdrawals")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WithdrawalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Withdrawal requested",
                withdrawalService.create(userDetails.getUsername(), WalletOwnerType.CUSTOMER, request)));
    }

    @Operation(summary = "UC-062 — Danh sách yêu cầu rút tiền của khách hàng", description = "Actor: **Customer**.")
    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> myWithdrawals(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                withdrawalService.listForOwner(userDetails.getUsername(), WalletOwnerType.CUSTOMER, pageable)));
    }
}
