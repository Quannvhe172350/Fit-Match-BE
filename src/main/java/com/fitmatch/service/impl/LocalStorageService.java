package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Storage dự phòng cho máy dev khi chưa cấu hình GCS ({@code GCS_ENABLED=false}).
 * Không dùng ở môi trường thật: file nằm trên đĩa của container nên mất khi
 * redeploy và không chia sẻ được giữa nhiều instance.
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private final String baseUrl;
    private final Path uploadDir;

    public LocalStorageService(String baseUrl, String uploadDir) {
        this.baseUrl = baseUrl;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not save the file");
        }
        log.info("[LOCAL-STORAGE] Saved {} ({} bytes)", objectKey, content.length);
        return new StoredObject(objectKey, "local", getUrl(objectKey));
    }

    @Override
    public String upload(String folder, String filename, MultipartFile file) {
        try {
            Path dir = resolve(folder);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
            String url = baseUrl + "/api/files/" + folder + "/" + filename;
            log.info("[LOCAL-STORAGE] Saved: {}", url);
            return url;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not save the file");
        }
    }

    @Override
    public byte[] download(String objectName) {
        try {
            return Files.readAllBytes(resolve(objectName));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File not found: " + objectName);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Files.deleteIfExists(resolve(objectName));
        } catch (IOException e) {
            log.warn("[LOCAL-STORAGE] Could not delete {}: {}", objectName, e.getMessage());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    @Override
    public void move(String sourceKey, String targetKey) {
        if (sourceKey == null || sourceKey.equals(targetKey)) return;
        Path source = resolve(sourceKey);
        Path target = resolve(targetKey);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not move the stored file");
        }
    }

    @Override
    public String getUrl(String objectKey) {
        // Key của media có nhiều cấp (gyms/1/gallery/uuid.jpg) nên không đi qua
        // /api/files/{folder}/{filename} được — MediaController có endpoint riêng.
        return baseUrl + "/api/media/raw/" + objectKey;
    }

    @Override
    public String getSignedUrl(String objectKey, Duration ttl) {
        // Không có cơ chế ký ở local; URL local vốn chỉ dùng khi chạy máy dev.
        return getUrl(objectKey);
    }

    @Override
    public String getBucketName() {
        return "local";
    }

    /** Chặn path traversal: mọi key phải nằm trong thư mục upload. */
    private Path resolve(String objectKey) {
        Path path = uploadDir.resolve(objectKey).normalize();
        if (!path.startsWith(uploadDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid file path");
        }
        return path;
    }
}
