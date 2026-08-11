package com.fitmatch.service;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Cổng duy nhất ra object storage. Nghiệp vụ (MediaService, ReviewService...)
 * chỉ được nói chuyện qua interface này — không import SDK của Google ở tầng
 * service — nên đổi GCS sang S3/MinIO chỉ là viết thêm một implementation.
 */
public interface StorageService {

    /** Kết quả upload: đủ thông tin để lưu vào DB và dựng lại URL sau này. */
    record StoredObject(String storageKey, String bucket, String publicUrl) {}

    /**
     * Upload theo object key đầy đủ do caller sinh (đã chứa uuid, không chứa tên
     * file của client). {@code publicUrl} chỉ khác null khi storage cho đọc ẩn danh.
     */
    StoredObject put(String objectKey, byte[] content, String contentType);

    /**
     * Upload kiểu cũ (folder + filename) trả thẳng URL — giữ cho
     * {@code /api/files/upload} (tài liệu KYC, chứng chỉ PT) chưa chuyển sang Media.
     */
    String upload(String folder, String filename, MultipartFile file);

    /** Tải nội dung theo object key. */
    byte[] download(String objectName);

    /** Xoá object (best-effort — không ném lỗi nếu object đã biến mất). */
    void delete(String objectName);

    /** Object có tồn tại thật trên storage không. */
    boolean exists(String objectKey);

    /**
     * Đổi vị trí object (copy + delete). Dùng khi ảnh nháp được gắn vào entity và
     * cần chuyển từ {@code reviews/pending/...} sang {@code reviews/{id}/images/...}.
     */
    void move(String sourceKey, String targetKey);

    /** URL đọc công khai, hoặc null nếu bucket không cho đọc ẩn danh. */
    String getUrl(String objectKey);

    /** URL có chữ ký, hết hạn sau {@code ttl}. Dùng cho bucket private. */
    String getSignedUrl(String objectKey, Duration ttl);

    /** Tên bucket/khu vực lưu trữ đang dùng — ghi vào metadata để truy vết. */
    String getBucketName();
}
