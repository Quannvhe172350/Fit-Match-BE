package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.UpdateCatalogStatusRequest;
import com.fitmatch.dto.ticket.TicketTypeRequest;
import com.fitmatch.dto.ticket.TicketTypeResponse;
import com.fitmatch.service.TicketTypeService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/ticket-types")
@RequiredArgsConstructor
@Tag(name = "G. Gym Ticket Types", description = "Catalog vé của Gym — thay dịch vụ + gói tập")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymTicketTypeController {

    private final TicketTypeService ticketTypeService;

    @Operation(summary = "Tạo loại vé",
            description = "Actor: **Gym Operator** (Gym đã APPROVED). kind=DAY (dayCount tự về 1) "
                    + "hoặc PACKAGE (dayCount >= 2). branchIds là các chi nhánh được bán vé này.")
    @PostMapping
    public ResponseEntity<ApiResponse<TicketTypeResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ticket type created",
                        ticketTypeService.create(userDetails.getUsername(), request)));
    }

    @Operation(summary = "Cập nhật loại vé",
            description = "Actor: **Gym Operator** (chủ sở hữu). Vé ĐÃ BÁN không đổi theo — "
                    + "giá và số ngày đã snapshot tại thời điểm mua. Lỗi: 409 vé đã ARCHIVED.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketTypeResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ticket type updated",
                ticketTypeService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Ẩn loại vé", description = "Actor: **Gym Operator**. Đặt active=false + HIDDEN.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        ticketTypeService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Ticket type deactivated", null));
    }

    @Operation(summary = "Liệt kê loại vé của Gym mình", description = "Actor: **Gym Operator**.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TicketTypeResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(ticketTypeService.list(userDetails.getUsername())));
    }

    @Operation(summary = "Đổi trạng thái vòng đời loại vé",
            description = "Actor: **Gym Operator**. PUBLISHED / HIDDEN / PAUSED / ARCHIVED; "
                    + "ARCHIVED là trạng thái cuối.")
    @PatchMapping("/{id}/catalog-status")
    public ResponseEntity<ApiResponse<TicketTypeResponse>> updateCatalogStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCatalogStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Catalog status updated",
                ticketTypeService.updateCatalogStatus(userDetails.getUsername(), id, request.getStatus())));
    }
}
