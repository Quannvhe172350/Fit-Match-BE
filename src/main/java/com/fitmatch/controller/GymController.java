package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.dto.gym.UpdateGymProfileRequest;
import com.fitmatch.service.GymProfileService;
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
@RequestMapping("/api/gym")
@RequiredArgsConstructor
@Tag(name = "F. Gym Onboarding", description = "Đăng ký & xác minh Gym (UC-41 → UC-44)")
@SecurityRequirement(name = "bearerAuth")
public class GymController {

    private final GymProfileService gymProfileService;

    @Operation(
            summary = "UC-41 — Nộp hồ sơ đăng ký Gym & tài liệu",
            description = """
                    Actor: **Authenticated user** (chưa là Gym Operator). Tạo hồ sơ Gym PENDING kèm tài liệu (URL).
                    Admin duyệt (UC-45/46); khi duyệt, tài khoản được nâng role ROLE_GYM_OPERATOR.
                    Lỗi: 409 đã có hồ sơ Gym; 400 validation.
                    """)
    @PostMapping("/registration")
    public ResponseEntity<ApiResponse<GymProfileResponse>> submitRegistration(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitGymRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gym registration submitted for verification",
                        gymProfileService.submitRegistration(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-42 — Xem trạng thái xác minh Gym",
            description = "Actor: **Gym applicant**. Xem hồ sơ Gym của mình kèm verificationStatus và lý do từ chối. Lỗi: 404 chưa nộp hồ sơ.")
    @GetMapping("/verification-status")
    public ResponseEntity<ApiResponse<GymProfileResponse>> getVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(gymProfileService.getOwnProfile(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-43 — Nộp lại hồ sơ xác minh Gym",
            description = "Actor: **Gym applicant**. Chỉ khi đang REJECTED; cập nhật + nộp lại -> PENDING. Lỗi: 409 không ở REJECTED; 404 chưa có hồ sơ.")
    @PutMapping("/registration/resubmit")
    public ResponseEntity<ApiResponse<GymProfileResponse>> resubmit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitGymRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gym registration resubmitted for verification",
                gymProfileService.resubmitRegistration(userDetails.getUsername(), request)));
    }
}
