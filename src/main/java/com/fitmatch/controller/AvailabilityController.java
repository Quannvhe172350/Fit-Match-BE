package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.request.AvailabilityCheckRequest;
import com.fitmatch.dto.response.AvailabilityCheckResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.support.ScheduleConflictValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
@Tag(name = "C. Availability", description = "Kiểm tra khả dụng lịch trước khi đặt (UC-030).")
@SecurityRequirement(name = "bearerAuth")
public class AvailabilityController {

    private final ScheduleConflictValidator scheduleConflictValidator;

    @Operation(
            summary = "UC-030 — Kiểm tra xung đột lịch",
            description = "Actor: **Authenticated**. Kiểm tra PT và/hoặc chi nhánh có nhận được khung giờ [startAt, endAt) không: trạng thái, lịch rảnh/giờ mở cửa, blocked time. Trả về available + danh sách lý do. Lỗi: 400 thiếu ptId lẫn branchId; 404 PT/branch không tồn tại.")
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AvailabilityCheckResponse>> check(
            @Valid @RequestBody AvailabilityCheckRequest request) {
        if (request.getPtId() == null && request.getBranchId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At least one of ptId or branchId is required");
        }
        List<String> reasons = new ArrayList<>();
        if (request.getPtId() != null) {
            reasons.addAll(scheduleConflictValidator.checkPt(
                    request.getPtId(), request.getStartAt(), request.getEndAt()));
        }
        if (request.getBranchId() != null) {
            reasons.addAll(scheduleConflictValidator.checkBranch(
                    request.getBranchId(), request.getStartAt(), request.getEndAt()));
        }
        return ResponseEntity.ok(ApiResponse.success(AvailabilityCheckResponse.of(reasons)));
    }
}
