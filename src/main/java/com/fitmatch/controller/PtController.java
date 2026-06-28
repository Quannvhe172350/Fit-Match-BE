package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
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
}
