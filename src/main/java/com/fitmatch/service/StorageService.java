package com.fitmatch.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** Upload file, trả về GCS object name (vd: "avatars/uuid.jpg"). */
    String upload(String folder, String filename, MultipartFile file);

    /** Tải nội dung file theo object name. */
    byte[] download(String objectName);

    /** Xoá object theo object name (best-effort). */
    void delete(String objectName);
}
