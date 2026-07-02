package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.FileUploadResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "C. Files", description = "Upload & truy xuất file (avatar, tài liệu, chứng chỉ)")
public class FileController {

    private static final Set<String> ALLOWED_FOLDERS = Set.of("avatars", "documents", "certifications");

    private final StorageService storageService;

    @Operation(
            summary = "Upload file",
            description = "Actor: **Authenticated user**. Upload file lên storage, trả về URL để dùng cho các API khác. "
                    + "Folder hợp lệ: `avatars`, `documents`, `certifications`.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "documents") String folder) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File must not be empty");
        }
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Invalid folder. Allowed: " + ALLOWED_FOLDERS);
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String filename = UUID.randomUUID() + "_" + original;
        String url = storageService.upload(folder, filename, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded", new FileUploadResponse(url)));
    }

    @Operation(summary = "Tải file theo folder/filename", description = "Actor: **Guest**. Public endpoint phục vụ file đã upload.")
    @SecurityRequirements
    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<byte[]> getFile(
            @PathVariable String folder,
            @PathVariable String filename) {
        byte[] data = storageService.download(folder + "/" + filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectContentType(filename)))
                .header("Cache-Control", "public, max-age=86400")
                .body(data);
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        return "image/jpeg";
    }
}
