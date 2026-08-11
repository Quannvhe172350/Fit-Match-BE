package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.config.MediaProperties;
import com.fitmatch.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kiểm tra file ảnh trước khi đẩy lên storage.
 *
 * <p>Điểm mấu chốt: KHÔNG tin {@code Content-Type} hay phần mở rộng do client
 * gửi — cả hai đều do người upload tự đặt. MIME thật được đọc từ magic bytes ở
 * đầu file, và chỉ MIME đó mới được ghi vào DB/storage. Nhờ vậy một file HTML
 * đổi tên thành {@code .jpg} với header {@code image/jpeg} vẫn bị chặn (nếu lọt,
 * bucket public sẽ phục vụ nó như trang web → stored XSS trên domain storage).
 */
@Component
@RequiredArgsConstructor
public class ImageFileValidator {

    private final MediaProperties properties;

    /** MIME thật của một file ảnh, đã đối chiếu với whitelist cấu hình. */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File must not be empty");
        }
        if (file.getSize() > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Image exceeds the " + properties.getMaxImageSizeMb() + "MB size limit");
        }
        byte[] head = readHead(file);
        String detected = detectMimeType(head);
        if (detected == null || !properties.getAllowedMimeTypes().contains(detected)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported image format. Allowed: " + String.join(", ", properties.getAllowedMimeTypes()));
        }
        return detected;
    }

    public void validateBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At least one file is required");
        }
        if (files.length > properties.getMaxFilesPerRequest()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At most " + properties.getMaxFilesPerRequest() + " files per upload");
        }
    }

    /** Đuôi file chuẩn hoá theo MIME thật — không lấy từ tên file client gửi. */
    public String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    /**
     * Nhận dạng theo chữ ký nhị phân. Chỉ những định dạng ảnh raster phổ biến;
     * SVG cố tình không được hỗ trợ vì SVG chứa script được.
     */
    public String detectMimeType(byte[] head) {
        if (head == null || head.length < 12) return null;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "image/gif";
        }
        return null;
    }

    private byte[] readHead(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(16);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Could not read the uploaded file");
        }
    }
}
