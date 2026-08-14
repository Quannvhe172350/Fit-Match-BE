package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.PtSlotCellDto;
import com.fitmatch.service.PtDailyAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tìm PT theo HAI CHIỀU. Khách hoặc chọn giờ trước rồi xem PT nào rảnh, hoặc
 * chọn PT trước rồi xem PT đó rảnh ngày nào — hai đường vào cùng một dữ liệu.
 */
@RestController
@RequestMapping("/api/pt-availability")
@RequiredArgsConstructor
@Tag(name = "C. PT Availability", description = "Tìm PT rảnh theo giờ hoặc theo lưới ngày")
@SecurityRequirement(name = "bearerAuth")
public class PtAvailabilitySearchController {

    private final PtDailyAvailabilityService availabilityService;

    @Operation(summary = "Chọn giờ trước — PT nào rảnh khung này",
            description = "Actor: người dùng đã đăng nhập.Trả về các PT ACTIVE của chi nhánh đã khai đúng khung giờ đó "
                    + "và chưa bị đặt.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PtSlotCellDto>>> search(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.search(branchId, date, startTime)));
    }

    @Operation(summary = "Lưới ngày x giờ",
            description = "Actor: người dùng đã đăng nhập.Có ptId = lưới của riêng PT đó; bỏ ptId = lưới gộp toàn bộ PT "
                    + "của chi nhánh. Ô taken=true là đã có người đặt (FE hiển thị mờ).")
    @GetMapping("/grid")
    public ResponseEntity<ApiResponse<List<PtSlotCellDto>>> grid(
            @RequestParam Long branchId,
            @RequestParam(required = false) Long ptId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.grid(branchId, ptId, from, to)));
    }
}
