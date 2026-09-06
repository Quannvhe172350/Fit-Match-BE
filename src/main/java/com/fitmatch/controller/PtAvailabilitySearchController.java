package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.PtSlotCellDto;
import com.fitmatch.service.PtSlotQueryService;
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
 *
 * <p>V85 giữ nguyên đường dẫn và hình dạng dữ liệu; chỉ NGUỒN đổi: slot giờ
 * sinh từ ca Gym đã xếp trừ đi đơn nghỉ đã duyệt, thay vì khung giờ PT tự khai.
 */
@RestController
@RequestMapping("/api/pt-availability")
@RequiredArgsConstructor
@Tag(name = "C. PT Availability", description = "Tìm PT rảnh theo giờ hoặc theo lưới ngày")
@SecurityRequirement(name = "bearerAuth")
public class PtAvailabilitySearchController {

    private final PtSlotQueryService slotQueryService;

    @Operation(summary = "Chọn giờ trước — PT nào rảnh khung này",
            description = "Actor: người dùng đã đăng nhập. Trả về các PT ACTIVE của chi nhánh CÓ CA phủ đúng "
                    + "khung giờ đó, không nghỉ phép và chưa bị đặt.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PtSlotCellDto>>> search(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) Integer minutes) {
        return ResponseEntity.ok(ApiResponse.success(
                slotQueryService.search(branchId, date, startTime, minutes)));
    }

    @Operation(summary = "Lưới ngày x giờ",
            description = "Actor: người dùng đã đăng nhập. Có ptId = lưới của riêng PT đó; bỏ ptId = lưới gộp "
                    + "toàn bộ PT của chi nhánh. Slot sinh từ ca đã xếp; slot bị đơn nghỉ ĐÃ DUYỆT phủ thì "
                    + "không xuất hiện. Ô taken=true là đã có người đặt (FE hiển thị mờ). "
                    + "`minutes` = thời lượng buổi ghi trên vé: mỗi ô khi đó là một khung có thể "
                    + "BẮT ĐẦU buổi dài ngần ấy phút, chuỗi slot phía sau được phép vắt qua nhiều "
                    + "ca liền nhau và endTime là điểm kết thúc của cả chuỗi. Bỏ trống = một slot. "
                    + "Lỗi: 400 khoảng ngày quá 92 ngày; 409 ptId không thuộc chi nhánh.")
    @GetMapping("/grid")
    public ResponseEntity<ApiResponse<List<PtSlotCellDto>>> grid(
            @RequestParam Long branchId,
            @RequestParam(required = false) Long ptId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer minutes) {
        return ResponseEntity.ok(ApiResponse.success(
                slotQueryService.grid(branchId, ptId, from, to, minutes)));
    }
}
