package com.fitmatch.service.impl;

import com.fitmatch.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class LocalStorageService implements StorageService {

    @Override
    public String upload(String folder, String filename, MultipartFile file) {
        log.warn("[STORAGE-STUB] GCS not enabled — upload skipped for {}/{}", folder, filename);
        return null;
    }

    @Override
    public byte[] download(String objectName) {
        throw new UnsupportedOperationException("GCS not configured in this environment");
    }

    @Override
    public void delete(String objectName) {
        log.warn("[STORAGE-STUB] GCS not enabled — delete skipped for {}", objectName);
    }
}
