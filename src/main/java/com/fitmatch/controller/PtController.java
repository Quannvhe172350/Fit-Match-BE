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
@Tag(name = "D. PT Onboarding", description = "Đăng ký & xác minh Personal Trainer (UC-23 → UC-28)")
@SecurityRequirement(name = "bearerAuth")
public class PtController {

    private final PtProfileService ptProfileService;

    @Operation(
            summary = "UC-23 — Nộp hồ sơ đăng ký PT & tài liệu",
            description = """
                    Actor: **Authenticated user** (chưa là PT). Tạo hồ sơ PT ở trạng thái PENDING kèm tài liệu xác minh
                    (file biểu diễn bằng URL). Admin sẽ duyệt (UC-29/30); khi duyệt, tài khoản được nâng role ROLE_PT.
                    Lỗi: 409 đã có hồ sơ PT; 400 validation.
                    """)
    @PostMapping("/registration")
    public ResponseEntity<ApiResponse<PtProfileResponse>> submitRegistration(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        PtProfileResponse response = ptProfileService.submitRegistration(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PT registration submitted for verification", response));
    }

    @Operation(
            summary = "UC-24 — Xem trạng thái xác minh PT",
            description = "Actor: **PT applicant**. Xem hồ sơ PT của chính mình kèm verificationStatus và lý do từ chối (nếu có). Lỗi: 404 chưa nộp hồ sơ.")
    @GetMapping("/verification-status")
    public ResponseEntity<ApiResponse<PtProfileResponse>> getVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(ptProfileService.getOwnProfile(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-25 — Nộp lại hồ sơ xác minh PT",
            description = """
                    Actor: **PT applicant**. Cập nhật thông tin + tài liệu và nộp lại; chỉ áp dụng khi hồ sơ đang REJECTED.
                    Đặt lại status -> PENDING và xoá lý do từ chối. Lỗi: 409 nếu không ở trạng thái REJECTED; 404 chưa có hồ sơ.
                    """)
    @PutMapping("/registration/resubmit")
    public ResponseEntity<ApiResponse<PtProfileResponse>> resubmit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitPtRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT registration resubmitted for verification",
                ptProfileService.resubmitRegistration(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-26 — Cập nhật hồ sơ & khu vực phục vụ PT",
            description = "Actor: **PT**. Cập nhật một phần hồ sơ (displayName/bio/serviceArea/specialization/experienceYears). Không đổi trạng thái xác minh. Lỗi: 404 chưa có hồ sơ.")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<PtProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePtProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT profile updated",
                ptProfileService.updateProfile(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-28 — Xem trước hồ sơ PT công khai",
            description = "Actor: **PT**. Xem hồ sơ của mình theo góc nhìn công khai (kèm chứng chỉ, không gồm tài liệu nội bộ). Lỗi: 404 chưa có hồ sơ.")
    @GetMapping("/profile/preview")
    public ResponseEntity<ApiResponse<PtPublicProfileResponse>> publicPreview(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                ptProfileService.getOwnPublicPreview(userDetails.getUsername())));
    }
}
