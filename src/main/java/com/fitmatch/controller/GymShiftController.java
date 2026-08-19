package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymShiftRequest;
import com.fitmatch.dto.gym.GymShiftResponse;
import com.fitmatch.dto.gym.ShiftRosterCellDto;
import com.fitmatch.service.GymShiftService;
import com.fitmatch.service.PtShiftRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * V85: Gym khai CA làm việc cho từng chi nhánh — đảo lại mô hình cũ nơi PT tự
 * khai khung giờ rảnh và gym không cấu hình hộ.
 */
@RestController
@RequestMapping("/api/gym")
@RequiredArgsConstructor
@Tag(name = "G. Gym Shifts", description = "Ca làm việc của chi nhánh và lưới phân ca")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymShiftController {

    private final GymShiftService shiftService;
    private final PtShiftRosterService rosterService;

    @Operation(summary = "Danh sách ca của chi nhánh",
            description = "Actor: **Gym Operator**. slotCount = số khung giờ khách đặt được mỗi lần "
                    + "lên ca. Lỗi: 404 chi nhánh không thuộc Gym.")
    @GetMapping("/branches/{branchId}/shifts")
    public ResponseEntity<ApiResponse<List<GymShiftResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftService.list(userDetails.getUsername(), branchId)));
    }

    @Operation(summary = "Tạo ca cho chi nhánh",
            description = "Actor: **Gym Operator**. Ca phải nằm TRONG giờ mở cửa của mọi thứ nó áp "
                    + "dụng, không chồng giờ với ca khác cùng chi nhánh, độ dài chia hết cho "
                    + "slotMinutes, và KHÔNG được vắt qua nửa đêm. "
                    + "Lỗi: 400 vi phạm các ràng buộc trên (thông điệp gộp mọi lý do); "
                    + "404 chi nhánh không thuộc Gym.")
    @PostMapping("/branches/{branchId}/shifts")
    public ResponseEntity<ApiResponse<GymShiftResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long branchId,
            @Valid @RequestBody GymShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Shift created",
                shiftService.create(userDetails.getUsername(), branchId, request)));
    }

    @Operation(summary = "Cập nhật ca",
            description = "Actor: **Gym Operator**. Đổi giờ hoặc slotMinutes bị CHẶN khi ca đã có "
                    + "buổi tập được đặt — buổi cũ sẽ rơi ra ngoài lưới slot mới. "
                    + "Lỗi: 400 ca không hợp lệ; 409 còn buổi đã đặt; 404 ca/chi nhánh không thuộc Gym.")
    @PutMapping("/branches/{branchId}/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<GymShiftResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long branchId,
            @PathVariable Long shiftId,
            @Valid @RequestBody GymShiftRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Shift updated",
                shiftService.update(userDetails.getUsername(), branchId, shiftId, request)));
    }

    @Operation(summary = "Xoá ca",
            description = "Actor: **Gym Operator**. Xoá luôn các dòng phân ca của ca này. "
                    + "Lỗi: 409 còn buổi tập đã đặt trong ca (kèm danh sách buổi vướng); "
                    + "404 ca/chi nhánh không thuộc Gym.")
    @DeleteMapping("/branches/{branchId}/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long branchId,
            @PathVariable Long shiftId) {
        shiftService.delete(userDetails.getUsername(), branchId, shiftId);
        return ResponseEntity.ok(ApiResponse.success("Shift deleted", null));
    }

    @Operation(summary = "Lưới phân ca của chi nhánh",
            description = "Actor: **Gym Operator**. Trả về danh sách ô PHẲNG — FE tự gom theo "
                    + "(ptProfileId, date) để dựng lưới hàng PT x cột ngày. onLeave=true là PT đã "
                    + "được duyệt nghỉ ca đó; bookedSessions là số buổi khách đã đặt trong ca. "
                    + "Lỗi: 400 khoảng ngày quá 366 ngày.")
    @GetMapping("/shift-roster")
    public ResponseEntity<ApiResponse<List<ShiftRosterCellDto>>> roster(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                rosterService.roster(userDetails.getUsername(), branchId, from, to)));
    }
}
