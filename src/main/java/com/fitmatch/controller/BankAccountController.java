package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.payment.BankAccountRequest;
import com.fitmatch.dto.payment.BankAccountResponse;
import com.fitmatch.dto.payment.BankResponse;
import com.fitmatch.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tài khoản ngân hàng thụ hưởng của chính người dùng (V61) — dùng chung cho Gym
 * Operator, PT và khách hàng, vì cả ba đều rút tiền theo cùng một luồng.
 */
@RestController
@RequestMapping("/api/me/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "C. Me - Bank accounts", description = "Tài khoản ngân hàng nhận tiền rút (V61). Mọi vai trò đã đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @Operation(
            summary = "Danh sách ngân hàng hỗ trợ",
            description = "Master data kèm mã BIN chuẩn VietQR — BIN là thứ cho phép hệ thống dựng QR chuyển khoản khi Finance duyệt lệnh rút. Tài khoản mặc định đứng đầu danh sách.")
    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<BankResponse>>> banks() {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.listBanks()));
    }

    @Operation(summary = "Tài khoản ngân hàng đã lưu của tôi")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> listMine(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                bankAccountService.listMine(userDetails.getUsername())));
    }

    @Operation(
            summary = "Thêm tài khoản ngân hàng",
            description = "Tài khoản đầu tiên tự động thành mặc định. Lỗi: 400 trùng tài khoản đã lưu hoặc vượt giới hạn 10 tài khoản.")
    @PostMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Bank account added",
                bankAccountService.create(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "Sửa tài khoản ngân hàng",
            description = "Không ảnh hưởng lệnh rút đã gửi — lệnh rút giữ bản sao thông tin ngân hàng tại thời điểm tạo.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bank account updated",
                bankAccountService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Đặt làm tài khoản mặc định")
    @PostMapping("/{id}/default")
    public ResponseEntity<ApiResponse<BankAccountResponse>> setDefault(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Default bank account updated",
                bankAccountService.setDefault(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Xoá tài khoản ngân hàng")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        bankAccountService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Bank account removed", null));
    }
}
