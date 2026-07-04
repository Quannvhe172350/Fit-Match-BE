package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pt")
@RequiredArgsConstructor
@Tag(name = "D. PT Self-service", description = "PT tự quản lý hồ sơ cá nhân giới hạn (UC-007). Các flow self-registration cũ đã DEPRECATED — PT do Gym tạo (UC-019).")
@SecurityRequirement(name = "bearerAuth")
public class PtController {

    private final PtProfileService ptProfileService;

    /** @deprecated Mô hình mới (UC-019): PT do Gym tạo qua POST /api/gym/pts. */
    @Deprecated
    @Operation(
            deprecated = true,
            summary = "[DEPRECATED] Nộp hồ sơ đăng ký PT & tài liệu",
            description = """
                    **DEPRECATED — mô hình PT self-registration đã bỏ (Use Case mới UC-019).**
                    Thay bằng: Gym tạo PT qua POST /api/gym/pts. Endpoint giữ tạm cho client cũ.
                    """)
    @PostMapping("/registration")
    public ResponseEntity<ApiResponse<PtProfileResponse>> submitRegistration(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        PtProfileResponse response = ptProfileService.submitRegistration(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PT registration submitted for verification", response));
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
            summary = "[DEPRECATED] Nộp lại hồ sơ xác minh PT",
            description = "**DEPRECATED — mô hình PT self-verification đã bỏ (UC-019).** Endpoint giữ tạm cho client cũ.")
    @PutMapping("/registration/resubmit")
    public ResponseEntity<ApiResponse<PtProfileResponse>> resubmit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT registration resubmitted for verification",
                ptProfileService.resubmitRegistration(userDetails.getUsername(), request)));
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
