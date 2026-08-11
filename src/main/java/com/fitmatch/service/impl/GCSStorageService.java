package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.StorageService;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Implementation Google Cloud Storage. Bucket/credentials đến từ cấu hình
 * ({@code app.gcs.*}) — không hard-code ở đâu trong mã nguồn.
 *
 * <p>{@code publicRead=true}: bucket được cấp quyền đọc ẩn danh (IAM allUsers →
 * roles/storage.objectViewer) nên URL công khai dùng thẳng được, hợp với ảnh
 * marketplace và cho phép đặt CDN phía trước. {@code publicRead=false}: mọi URL
 * đều được ký lại với thời hạn ngắn, credentials không bao giờ rời khỏi backend.
 */
@Slf4j
public class GCSStorageService implements StorageService {

    private static final String GCS_PUBLIC_BASE = "https://storage.googleapis.com/";

    private final Storage storage;
    private final String bucket;
    private final boolean publicRead;

    public GCSStorageService(Storage storage, String bucket, boolean publicRead) {
        this.storage = storage;
        this.bucket = bucket;
        this.publicRead = publicRead;
    }

    @Override
    public StoredObject put(String objectKey, byte[] content, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey))
                .setContentType(contentType)
                // Ảnh là immutable (tên có uuid) nên cho cache dài ở browser/CDN.
                .setCacheControl(publicRead ? "public, max-age=31536000, immutable" : "private, max-age=0")
                .build();
        try {
            storage.create(blobInfo, content);
        } catch (StorageException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Could not upload the file to storage - please try again");
        }
        log.info("Uploaded to GCS: gs://{}/{} ({} bytes)", bucket, objectKey, content.length);
        return new StoredObject(objectKey, bucket, publicRead ? publicUrl(objectKey) : null);
    }

    @Override
    public String upload(String folder, String filename, MultipartFile file) {
        String objectName = folder + "/" + filename;
        try {
            put(objectName, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not read the uploaded file");
        }
        return publicUrl(objectName);
    }

    @Override
    public byte[] download(String objectName) {
        Blob blob = storage.get(BlobId.of(bucket, toKey(objectName)));
        if (blob == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File not found: " + objectName);
        }
        return blob.getContent();
    }

    @Override
    public void delete(String objectName) {
        if (objectName == null || objectName.isBlank()) return;
        String key = toKey(objectName);
        try {
            storage.delete(BlobId.of(bucket, key));
            log.info("Deleted from GCS: gs://{}/{}", bucket, key);
        } catch (Exception e) {
            // Best-effort: xoá object thất bại không được làm hỏng giao dịch nghiệp vụ.
            log.warn("Could not delete GCS object {}: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            return storage.get(BlobId.of(bucket, toKey(objectKey))) != null;
        } catch (Exception e) {
            log.warn("Could not stat GCS object {}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    @Override
    public void move(String sourceKey, String targetKey) {
        if (sourceKey == null || sourceKey.equals(targetKey)) return;
        BlobId source = BlobId.of(bucket, toKey(sourceKey));
        storage.copy(Storage.CopyRequest.of(source, BlobId.of(bucket, targetKey))).getResult();
        delete(sourceKey);
    }

    @Override
    public String getUrl(String objectKey) {
        return publicRead ? publicUrl(toKey(objectKey)) : null;
    }

    @Override
    public String getSignedUrl(String objectKey, Duration ttl) {
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, toKey(objectKey))).build();
        try {
            URL signed = storage.signUrl(info, ttl.toMinutes(), TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature());
            return signed.toString();
        } catch (Exception e) {
            // ADC trên GCE ký được qua IAM SignBlob, nhưng cần quyền
            // roles/iam.serviceAccountTokenCreator. Thiếu quyền thì đừng làm vỡ
            // trang — trả URL công khai (bucket public) hoặc null.
            log.warn("Could not sign URL for {}: {}", objectKey, e.getMessage());
            return publicRead ? publicUrl(toKey(objectKey)) : null;
        }
    }

    @Override
    public String getBucketName() {
        return bucket;
    }

    private String publicUrl(String objectKey) {
        return GCS_PUBLIC_BASE + bucket + "/" + objectKey;
    }

    /** Chấp nhận cả object key lẫn URL công khai đầy đủ (dữ liệu cũ lưu nguyên URL). */
    private String toKey(String objectNameOrUrl) {
        String prefix = GCS_PUBLIC_BASE + bucket + "/";
        return objectNameOrUrl.startsWith(prefix)
                ? objectNameOrUrl.substring(prefix.length())
                : objectNameOrUrl;
    }
}
