package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.payment.WalletAdminActionRequest;
import com.fitmatch.service.AdminWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wallets")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Wallets",
        description = "UC-060: đóng băng / gỡ đóng băng ví gym khi có rủi ro/tranh chấp. Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    @Operation(
            summary = "UC-060 — Đóng băng số dư ví gym",
            description = "Actor: **Admin/Finance**. Chuyển một phần available -> frozen kèm lý do (ghi audit + ledger FREEZE). `{gymProfileId}` là id hồ sơ gym. Lỗi: 400 available không đủ; 404 ví không tồn tại.")
    @PostMapping("/{gymProfileId}/freeze")
    public ResponseEntity<ApiResponse<Void>> freeze(
            @PathVariable Long gymProfileId,
            @Valid @RequestBody WalletAdminActionRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        adminWalletService.freeze(gymProfileId, request.getAmount(), request.getReason(), actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Wallet frozen", null));
    }

    @Operation(
            summary = "UC-060 — Gỡ đóng băng số dư ví gym",
            description = "Actor: **Admin/Finance**. Chuyển một phần frozen -> available kèm lý do (ghi audit + ledger UNFREEZE). Lỗi: 400 frozen không đủ; 404 ví không tồn tại.")
    @PostMapping("/{gymProfileId}/unfreeze")
    public ResponseEntity<ApiResponse<Void>> unfreeze(
            @PathVariable Long gymProfileId,
            @Valid @RequestBody WalletAdminActionRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        adminWalletService.unfreeze(gymProfileId, request.getAmount(), request.getReason(), actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Wallet unfrozen", null));
    }
}
