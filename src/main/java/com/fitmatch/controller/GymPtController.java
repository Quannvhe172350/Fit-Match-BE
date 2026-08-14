package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.CreateGymPtRequest;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.dto.pt.PtAssignmentResponse;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtDocumentResponse;
import com.fitmatch.dto.pt.UpdateGymPtRequest;
import com.fitmatch.dto.pt.UpdatePtStatusRequest;
import com.fitmatch.service.GymPtManagementService;
import com.fitmatch.service.GymPtQualificationService;
import com.fitmatch.service.PtAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gym/pts")
@RequiredArgsConstructor
@Tag(name = "G. Gym - PT Management",
        description = "Gym tạo và quản lý PT dưới quyền mình (UC-019..021). Yêu cầu ROLE_GYM_OPERATOR + Gym đã APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymPtController {

    private final GymPtManagementService service;
    private final GymPtQualificationService qualificationService;
    private final PtAssignmentService assignmentService;

    @Operation(
            summary = "UC-019 — Tạo PT dưới quyền Gym",
            description = "Actor: **Gym Operator**. Tạo tài khoản (ROLE_PT) + hồ sơ PT thuộc Gym; PT ACTIVE ngay, không qua platform verification. Lỗi: 409 username/email trùng hoặc Gym chưa APPROVED; 404 chưa có hồ sơ Gym.")
    @PostMapping
    public ResponseEntity<ApiResponse<GymPtResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateGymPtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PT created", service.createPt(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-019 — Danh sách PT của Gym",
            description = "Actor: **Gym Operator**. Liệt kê PT thuộc Gym, phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GymPtResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(userDetails.getUsername(), pageable)));
    }

    @Operation(
            summary = "UC-019 — Chi tiết PT của Gym",
            description = "Actor: **Gym Operator**. Xem chi tiết một PT thuộc Gym. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GymPtResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-023 — Hiệu suất & chất lượng PT",
            description = "Actor: **Gym Operator**. Tổng hợp điểm đánh giá, số buổi hoàn tất/hủy/vắng mặt và số tranh chấp của một PT. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{id}/performance")
    public ResponseEntity<ApiResponse<com.fitmatch.dto.pt.PtPerformanceResponse>> performance(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getPerformance(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-019 — Cập nhật hồ sơ PT của Gym",
            description = "Actor: **Gym Operator**. Cập nhật một phần hồ sơ PT (displayName/bio/specialization/serviceArea/experienceYears). Lỗi: 404 PT không thuộc Gym.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GymPtResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGymPtRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT profile updated",
                service.update(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-021 — Gym bật/tắt PT",
            description = "Actor: **Gym Operator**. Đặt trạng thái ACTIVE/INACTIVE cho PT thuộc Gym; PT INACTIVE ẩn khỏi marketplace và không nhận booking. Không đổi được PT đang bị Admin SUSPENDED. Lỗi: 409 trạng thái không hợp lệ; 404 PT không thuộc Gym.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<GymPtResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePtStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT status updated",
                service.updateStatus(userDetails.getUsername(), id, request.getStatus())));
    }

    // Lịch rảnh của PT giờ do CHÍNH PT khai theo ngày cụ thể
    // (GET/PUT /api/pt/availability/daily) — gym không cấu hình hộ nữa.

    // ==================== UC-022: Assignments ====================

    @Operation(
            summary = "UC-022 — Gán PT vào chi nhánh",
            description = "Actor: **Gym Operator**. Câu 24: đích duy nhất là chi nhánh (phải thuộc cùng Gym). Lỗi: 404 chi nhánh không thuộc Gym; 400 đã gán trước đó.")
    @PostMapping("/{ptId}/assignments")
    public ResponseEntity<ApiResponse<PtAssignmentResponse>> assign(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @Valid @RequestBody PtAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("PT assigned",
                assignmentService.assign(userDetails.getUsername(), ptId, request)));
    }

    @Operation(
            summary = "UC-022 — Gỡ gán PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 assignment không thuộc Gym.")
    @DeleteMapping("/{ptId}/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @PathVariable Long assignmentId) {
        assignmentService.remove(userDetails.getUsername(), ptId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success("Assignment removed", null));
    }

    @Operation(
            summary = "UC-022 — Danh sách gán của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{ptId}/assignments")
    public ResponseEntity<ApiResponse<List<PtAssignmentResponse>>> listAssignments(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long ptId) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.list(userDetails.getUsername(), ptId)));
    }

    // ==================== UC-020: Qualification records ====================

    @Operation(
            summary = "UC-020 — Thêm chứng chỉ cho PT",
            description = "Actor: **Gym Operator**. Ghi nhận chứng chỉ của PT thuộc Gym. Lỗi: 404 PT không thuộc Gym.")
    @PostMapping("/{ptId}/certifications")
    public ResponseEntity<ApiResponse<CertificationResponse>> addCertification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Certification added",
                qualificationService.addCertification(userDetails.getUsername(), ptId, request)));
    }

    @Operation(
            summary = "UC-020 — Cập nhật chứng chỉ của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT/chứng chỉ không thuộc Gym.")
    @PutMapping("/{ptId}/certifications/{certId}")
    public ResponseEntity<ApiResponse<CertificationResponse>> updateCertification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @PathVariable Long certId,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certification updated",
                qualificationService.updateCertification(userDetails.getUsername(), ptId, certId, request)));
    }

    @Operation(
            summary = "UC-020 — Xoá chứng chỉ của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT/chứng chỉ không thuộc Gym.")
    @DeleteMapping("/{ptId}/certifications/{certId}")
    public ResponseEntity<ApiResponse<Void>> deleteCertification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @PathVariable Long certId) {
        qualificationService.deleteCertification(userDetails.getUsername(), ptId, certId);
        return ResponseEntity.ok(ApiResponse.success("Certification deleted", null));
    }

    @Operation(
            summary = "UC-020 — Danh sách chứng chỉ của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{ptId}/certifications")
    public ResponseEntity<ApiResponse<List<CertificationResponse>>> listCertifications(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long ptId) {
        return ResponseEntity.ok(ApiResponse.success(
                qualificationService.listCertifications(userDetails.getUsername(), ptId)));
    }

    @Operation(
            summary = "UC-020 — Thêm tài liệu năng lực cho PT",
            description = "Actor: **Gym Operator**. Tài liệu biểu diễn bằng URL (upload qua /api/files/upload trước). Lỗi: 404 PT không thuộc Gym.")
    @PostMapping("/{ptId}/documents")
    public ResponseEntity<ApiResponse<PtDocumentResponse>> addDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @Valid @RequestBody PtDocumentDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document added",
                qualificationService.addDocument(userDetails.getUsername(), ptId, request)));
    }

    @Operation(
            summary = "UC-020 — Xoá tài liệu năng lực của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT/tài liệu không thuộc Gym.")
    @DeleteMapping("/{ptId}/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long ptId,
            @PathVariable Long documentId) {
        qualificationService.deleteDocument(userDetails.getUsername(), ptId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @Operation(
            summary = "UC-020 — Danh sách tài liệu năng lực của PT",
            description = "Actor: **Gym Operator**. Lỗi: 404 PT không thuộc Gym.")
    @GetMapping("/{ptId}/documents")
    public ResponseEntity<ApiResponse<List<PtDocumentResponse>>> listDocuments(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long ptId) {
        return ResponseEntity.ok(ApiResponse.success(
                qualificationService.listDocuments(userDetails.getUsername(), ptId)));
    }
}
