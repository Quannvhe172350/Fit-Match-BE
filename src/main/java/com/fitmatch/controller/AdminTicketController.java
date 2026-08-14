package com.fitmatch.controller;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.TicketExpiryConfigDto;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.service.AdminTicketService;
import com.fitmatch.service.PlatformTicketConfigService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "A. Admin Tickets", description = "Tra cứu vé và cấu hình hạn vé")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {

    private final AdminTicketService adminTicketService;
    private final PlatformTicketConfigService ticketConfigService;

    @Operation(summary = "Tra cứu vé toàn hệ thống", description = "Actor: **Admin**.")
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> tickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Long gymProfileId,
            @RequestParam(required = false) String customerUsername,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                adminTicketService.search(status, gymProfileId, customerUsername, pageable)));
    }

    @Operation(summary = "Chi tiết vé kèm ngày tập", description = "Actor: **Admin**.")
    @GetMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminTicketService.detail(id)));
    }

    @Operation(summary = "Xem cấu hình hạn dùng vé", description = "Actor: **Admin**.")
    @GetMapping("/config/ticket-expiry")
    public ResponseEntity<ApiResponse<TicketExpiryConfigDto>> expiryConfig() {
        return ResponseEntity.ok(ApiResponse.success(ticketConfigService.current()));
    }

    @Operation(summary = "Đổi hạn dùng vé",
            description = "Actor: **Admin**. Chỉ áp cho vé bán TỪ NAY — vé đã bán giữ nguyên "
                    + "expires_at đã chốt lúc mua.")
    @PutMapping("/config/ticket-expiry")
    public ResponseEntity<ApiResponse<TicketExpiryConfigDto>> updateExpiryConfig(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TicketExpiryConfigDto request) {
        return ResponseEntity.ok(ApiResponse.success("Ticket expiry updated",
                ticketConfigService.update(userDetails.getUsername(), request)));
    }
}
