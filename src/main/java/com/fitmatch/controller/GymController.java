package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.dto.gym.UpdateGymProfileRequest;
import com.fitmatch.dto.gym.UpdateGymVisibilityRequest;
import com.fitmatch.service.GymProfileService;
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
// BE-1 (audit 2026-07-17): trước đây không có @PreAuthorize — CUSTOMER/PT gọi được
// registration/profile. Sau P0-2, role GYM_OPERATOR được cấp ngay khi đăng ký
// accountType=GYM_OPERATOR nên siết được ở đây (ownership vẫn check ở service).
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymController {

    private final GymProfileService gymProfileService;

    @Operation(
            summary = "UC-41 — Nộp hồ sơ đăng ký Gym & tài liệu",
            description = """
                    Actor: **Gym Operator** (đăng ký với accountType=GYM_OPERATOR). Tạo hồ sơ Gym PENDING kèm tài liệu (URL).
                    Admin duyệt (UC-45/46). Lỗi: 409 đã có hồ sơ Gym; 400 validation.
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

    @Operation(
            summary = "UC-44 — Cập nhật hồ sơ Gym",
            description = "Actor: **Gym Operator**. Cập nhật một phần hồ sơ (gymName/description/address/city/phone). Không đổi trạng thái xác minh. Lỗi: 404 chưa có hồ sơ.")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<GymProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateGymProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gym profile updated",
                gymProfileService.updateProfile(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-018 — Hiển thị / ẩn hồ sơ Gym trên marketplace",
            description = "Actor: **Gym Operator**. Bật/tắt hiển thị công khai của Gym (chỉ khi APPROVED; Gym bị SUSPENDED không tự bật lại được). Lỗi: 409 chưa được duyệt; 404 chưa có hồ sơ.")
    @PutMapping("/profile/visibility")
    public ResponseEntity<ApiResponse<GymProfileResponse>> updateVisibility(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateGymVisibilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gym visibility updated",
                gymProfileService.updateVisibility(userDetails.getUsername(), request.getVisible())));
    }

    @Operation(
            summary = "UC-012 — Danh sách tài liệu xác minh của Gym",
            description = "Actor: **Gym Operator**. Xem các tài liệu KYC đã nộp của chính Gym.")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<java.util.List<com.fitmatch.dto.gym.GymDocumentDto>>> listDocuments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Documents",
                gymProfileService.listDocuments(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-012 — Thêm tài liệu xác minh",
            description = "Actor: **Gym Operator**. Thêm một tài liệu khi hồ sơ chưa/đang duyệt hoặc cần bổ sung. Lỗi: 409 nếu hồ sơ đã APPROVED/SUSPENDED.")
    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<com.fitmatch.dto.gym.GymDocumentDto>> addDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody com.fitmatch.dto.gym.GymDocumentDto request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Document added",
                        gymProfileService.addDocument(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-012 — Xoá tài liệu xác minh",
            description = "Actor: **Gym Operator**. Xoá tài liệu của chính Gym khi hồ sơ chưa/đang duyệt hoặc cần bổ sung.")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        gymProfileService.deleteDocument(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
