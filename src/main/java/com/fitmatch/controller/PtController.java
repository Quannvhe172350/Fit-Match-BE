package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;
import com.fitmatch.dto.pt.UpdatePtProfileRequest;
import com.fitmatch.service.PtProfileService;
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

@RestController
@RequestMapping("/api/pt")
@RequiredArgsConstructor
@Tag(name = "D. PT Self-service", description = "PT tự quản lý hồ sơ cá nhân giới hạn (UC-007). Các flow self-registration cũ đã bị vô hiệu hoá — PT do Gym tạo (UC-019).")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PT')")
public class PtController {

    private final PtProfileService ptProfileService;

    /** @deprecated Mô hình mới (UC-019): PT do Gym tạo qua POST /api/gym/pts. */
    @Deprecated
    @Operation(
            deprecated = true,
            summary = "[DISABLED] Nộp hồ sơ đăng ký PT & tài liệu",
            description = """
                    **410 GONE — mô hình PT self-registration đã bỏ (UC-019).**
                    PT do Gym tạo qua POST /api/gym/pts.
                    """)
    @PostMapping("/registration")
    public ResponseEntity<ApiResponse<PtProfileResponse>> submitRegistration(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        // UC-019: chặn cứng — luồng này mâu thuẫn mô hình "PT thuộc Gym".
        throw new BusinessException(ErrorCode.LEGACY_ENDPOINT_DISABLED);
    }

    /** @deprecated PT không còn qua platform verification (UC-019). */
    @Deprecated
    @Operation(
            deprecated = true,
            summary = "[DEPRECATED] Xem trạng thái xác minh PT",
            description = "**DEPRECATED — PT không còn qua platform verification (UC-019).** Dùng GET /api/pt/profile/preview để xem hồ sơ.")
    @GetMapping("/verification-status")
    public ResponseEntity<ApiResponse<PtProfileResponse>> getVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(ptProfileService.getOwnProfile(userDetails.getUsername())));
    }

    /** @deprecated Mô hình PT self-verification đã bỏ (UC-019). */
    @Deprecated
    @Operation(
            deprecated = true,
            summary = "[DISABLED] Nộp lại hồ sơ xác minh PT",
            description = "**410 GONE — mô hình PT self-verification đã bỏ (UC-019).** Hồ sơ PT do Gym quản lý.")
    @PutMapping("/registration/resubmit")
    public ResponseEntity<ApiResponse<PtProfileResponse>> resubmit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        // UC-019/007: chặn cứng — endpoint này còn cho phép PT tự sửa các field do Gym quản lý.
        throw new BusinessException(ErrorCode.LEGACY_ENDPOINT_DISABLED);
    }

    @Operation(
            summary = "UC-007 — PT tự cập nhật hồ sơ cá nhân (giới hạn)",
            description = "Actor: **PT**. Chỉ được tự sửa displayName và bio (giới thiệu cá nhân) theo chính sách Gym; specialization/serviceArea/experienceYears do Gym quản lý (UC-019/020). Lỗi: 404 chưa có hồ sơ.")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<PtProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePtProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT profile updated",
                ptProfileService.updateProfile(userDetails.getUsername(), request)));
    }

    // V85: lịch của PT do GYM xếp. PT chỉ ĐỌC ca của mình ở GET /api/pt/shifts
    // và gửi đơn nghỉ ở /api/pt/leave-requests — xem PtShiftController. Cả lịch
    // rảnh hằng tuần lẫn blocked-times của các mô hình trước đều không còn.

    @Operation(
            summary = "UC-007 — Xem trước hồ sơ PT công khai",
            description = "Actor: **PT**. Xem hồ sơ của mình theo góc nhìn công khai (kèm chứng chỉ, không gồm tài liệu nội bộ). Lỗi: 404 chưa có hồ sơ.")
    @GetMapping("/profile/preview")
    public ResponseEntity<ApiResponse<PtPublicProfileResponse>> publicPreview(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                ptProfileService.getOwnPublicPreview(userDetails.getUsername())));
    }
}
