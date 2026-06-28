package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.user.DeactivateAccountRequest;
import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "B. User", description = "Xem và cập nhật hồ sơ người dùng (UC-05)")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "UC-05 — Xem hồ sơ",
            description = "Actor: **Authenticated**. Trả về toàn bộ thông tin hồ sơ của người dùng hiện tại. Yêu cầu Bearer token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = userService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "UC-05 — Cập nhật hồ sơ",
            description = "Actor: **Authenticated**. Cập nhật thông tin hồ sơ (tên, giới tính, vị trí, chiều cao, cân nặng, mục tiêu, liên hệ khẩn cấp, sở thích tập luyện). Chỉ cần gửi các trường muốn thay đổi. Yêu cầu Bearer token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", response));
    }

    @Operation(
            summary = "UC-05 — Tải lên avatar",
            description = "Actor: **Authenticated**. Upload ảnh đại diện (JPEG/PNG/GIF/WebP, tối đa 5 MB). Lưu lên GCS và cập nhật `avatarUrl` trong hồ sơ. Yêu cầu Bearer token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        UserResponse response = userService.uploadAvatar(userDetails.getUsername(), file);
        return ResponseEntity.ok(ApiResponse.success("Avatar updated", response));
    }

    @Operation(
            summary = "UC-09 — Vô hiệu hoá tài khoản",
            description = """
                    Actor: **Authenticated**. Người dùng tự vô hiệu hoá tài khoản của mình (status → INACTIVE),
                    yêu cầu xác nhận mật khẩu. Sau đó không thể đăng nhập. Dữ liệu/lịch sử được giữ lại (audit).
                    Lỗi: 401 sai mật khẩu; 400 tài khoản đã bị vô hiệu hoá.
                    """)
    @PostMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@AuthenticationPrincipal UserDetails userDetails,
                                                        @Valid @RequestBody DeactivateAccountRequest request) {
        userService.deactivateAccount(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Account deactivated successfully", null));
    }
}
