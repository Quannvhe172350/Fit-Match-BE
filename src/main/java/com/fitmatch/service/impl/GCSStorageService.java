package com.fitmatch.service.impl;

import com.fitmatch.service.StorageService;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
public class GCSStorageService implements StorageService {

    private final Storage storage;
    private final String bucket;

    private static final String GCS_PUBLIC_BASE = "https://storage.googleapis.com/";

    public GCSStorageService(Storage storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public String upload(String folder, String filename, MultipartFile file) {
        String objectName = folder + "/" + filename;
        BlobId blobId = BlobId.of(bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();
        try {
            storage.create(blobInfo, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to GCS", e);
        }
        String publicUrl = GCS_PUBLIC_BASE + bucket + "/" + objectName;
        log.info("Uploaded to GCS: {}", publicUrl);
        return publicUrl;
    }

    @Override
    public byte[] download(String objectName) {
        String key = objectName.startsWith(GCS_PUBLIC_BASE)
                ? objectName.substring((GCS_PUBLIC_BASE + bucket + "/").length())
                : objectName;
        var blob = storage.get(BlobId.of(bucket, key));
        if (blob == null) throw new RuntimeException("File not found: " + key);
        return blob.getContent();
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) return;
        String prefix = GCS_PUBLIC_BASE + bucket + "/";
        String objectName = url.startsWith(prefix) ? url.substring(prefix.length()) : url;
        try {
            storage.delete(BlobId.of(bucket, objectName));
            log.info("Deleted from GCS: {}", objectName);
        } catch (Exception e) {
            log.warn("Could not delete GCS object {}: {}", objectName, e.getMessage());
        }
    }
}
