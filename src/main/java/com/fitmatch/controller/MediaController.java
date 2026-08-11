package com.fitmatch.controller;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.media.MediaUpdateRequest;
import com.fitmatch.dto.media.ReorderMediaRequest;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "C. Media", description = "Ảnh dùng chung cho toàn hệ thống (avatar, gym/chi nhánh, dịch vụ, gói tập, check-in, PT, đánh giá). "
        + "File nằm trên Google Cloud Storage; API chỉ trao đổi metadata + URL.")
public class MediaController {

    private final MediaService mediaService;
    private final StorageService storageService;

    /** Endpoint phục vụ file chỉ bật khi chạy storage local — xem {@link #raw}. */
    @Value("${app.gcs.enabled:false}")
    private boolean gcsEnabled;

    @Operation(
            summary = "Upload ảnh cho một entity",
            description = """
                    Actor: **Authenticated user** có quyền trên entity đó.
                    `entityId` bỏ trống = ảnh nháp (upload trước khi bản ghi tồn tại, ví dụ soạn đánh giá);
                    gắn vào bản ghi khi tạo review/check-in bằng danh sách `mediaIds`.
                    Kiểm tra phía server: quyền trên entity, dung lượng, và MIME thật đọc từ magic bytes
                    (không tin Content-Type của client). Lỗi: 400 file sai định dạng/quá lớn/quá số lượng; 403 không có quyền.""")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<MediaResponse>>> upload(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("files") MultipartFile[] files,
            @RequestParam MediaEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam MediaImageType imageType) {
        List<MediaResponse> uploaded = mediaService.upload(
                userDetails.getUsername(), entityType, entityId, imageType, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Images uploaded", uploaded));
    }

    @Operation(
            summary = "Danh sách ảnh của một entity",
            description = "Actor: **Guest** với ảnh hồ sơ công khai (gym/chi nhánh/dịch vụ/gói/PT/đánh giá); "
                    + "ảnh check-in yêu cầu đăng nhập và đúng chủ booking.")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<ApiResponse<List<MediaResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam MediaEntityType entityType,
            @RequestParam Long entityId,
            @RequestParam(required = false) MediaImageType imageType) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.list(username, entityType, entityId, imageType)));
    }

    @Operation(
            summary = "Danh sách ảnh có phân trang",
            description = "Actor: **Guest/Authenticated**. Dùng cho thư viện nhiều ảnh để không trả về toàn bộ.")
    @SecurityRequirements
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<MediaResponse>>> listPaged(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam MediaEntityType entityType,
            @RequestParam Long entityId,
            @RequestParam(required = false) MediaImageType imageType,
            @PageableDefault(size = 24) Pageable pageable) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.listPaged(username, entityType, entityId, imageType, pageable)));
    }

    @Operation(
            summary = "Sửa caption / ảnh chính / thứ tự",
            description = "Actor: **Chủ sở hữu entity hoặc Admin**. Đặt `primary=true` sẽ bỏ cờ ảnh chính cũ cùng loại.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody MediaUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Image updated",
                mediaService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "Sắp xếp lại thư viện ảnh",
            description = "Actor: **Chủ sở hữu entity hoặc Admin**. Lỗi: 400 nếu danh sách chứa ảnh không thuộc thư viện này.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/reorder")
    public ResponseEntity<ApiResponse<List<MediaResponse>>> reorder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam MediaEntityType entityType,
            @RequestParam Long entityId,
            @RequestParam MediaImageType imageType,
            @Valid @RequestBody ReorderMediaRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gallery reordered", mediaService.reorder(
                userDetails.getUsername(), entityType, entityId, imageType, request.getMediaIds())));
    }

    @Operation(
            summary = "Xoá ảnh",
            description = "Actor: **Chủ sở hữu entity hoặc Admin**. Xoá cả object trên storage sau khi commit. Lỗi: 403/404 nếu không có quyền.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        mediaService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Image deleted", null));
    }

    /**
     * Phục vụ file khi chạy storage local (máy dev). Ở chế độ GCS thì trả 404: ảnh
     * public đã có URL GCS/CDN, còn bucket private phải đi qua signed URL — mở
     * endpoint này sẽ là đường vòng qua đúng cơ chế bảo vệ đó.
     */
    @Operation(summary = "Tải file đã upload (chỉ khi dùng storage local)", description = "Actor: **Guest**. Không khả dụng khi bật GCS.")
    @SecurityRequirements
    @GetMapping("/raw/**")
    public ResponseEntity<byte[]> raw(HttpServletRequest request) {
        if (gcsEnabled) return ResponseEntity.notFound().build();
        // getRequestURI() thay vì attribute của HandlerMapping: với PathPatternParser
        // (mặc định từ Boot 3) attribute đó có thể null và ta sẽ NPE.
        String uri = request.getRequestURI();
        int marker = uri.indexOf("/media/raw/");
        if (marker < 0) return ResponseEntity.notFound().build();
        String key = uri.substring(marker + "/media/raw/".length());
        // LocalStorageService tự chặn path traversal; đây là lớp chặn thứ hai.
        if (key.isBlank() || key.contains("..")) return ResponseEntity.notFound().build();
        byte[] data = storageService.download(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeOf(key)))
                .header("Cache-Control", "public, max-age=86400")
                .body(data);
    }

    private String contentTypeOf(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
