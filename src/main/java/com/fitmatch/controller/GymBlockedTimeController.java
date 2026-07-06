package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.BlockedTimeRequest;
import com.fitmatch.dto.pt.BlockedTimeResponse;
import com.fitmatch.service.BlockedTimeService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/blocked-times")
@RequiredArgsConstructor
@Tag(name = "G. Gym Blocked Times",
        description = "Khoảng không nhận đặt lịch của PT/chi nhánh (UC-029). Yêu cầu ROLE_GYM_OPERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymBlockedTimeController {

    private final BlockedTimeService blockedTimeService;

    @Operation(summary = "UC-029 — Tạo blocked time cho PT hoặc chi nhánh",
            description = "Actor: **Gym Operator**. Truyền đúng MỘT trong ptId/branchId (thuộc Gym). Lỗi: 400 sai đích hoặc startAt >= endAt; 404 đích không thuộc Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<BlockedTimeResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BlockedTimeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Blocked time created",
                blockedTimeService.createForGym(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-029 — Xoá blocked time",
            description = "Actor: **Gym Operator**. Lỗi: 404 không thuộc phạm vi Gym.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        blockedTimeService.deleteForGym(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Blocked time deleted", null));
    }

    @Operation(summary = "UC-029 — Danh sách blocked time theo PT hoặc chi nhánh",
            description = "Actor: **Gym Operator**. Truyền ptId hoặc branchId. Lỗi: 400 thiếu tham số; 404 đích không thuộc Gym.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockedTimeResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long ptId,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(
                blockedTimeService.listForGym(userDetails.getUsername(), ptId, branchId)));
    }
}
