package com.fitmatch.service.impl;

import com.fitmatch.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class LocalStorageService implements StorageService {

    private final String baseUrl;
    private final Path uploadDir;

    public LocalStorageService(String baseUrl, String uploadDir) {
        this.baseUrl = baseUrl;
        this.uploadDir = Paths.get(uploadDir);
    }

    @Override
    public String upload(String folder, String filename, MultipartFile file) {
        try {
            Path dir = uploadDir.resolve(folder);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
            String url = baseUrl + "/api/files/" + folder + "/" + filename;
            log.info("[LOCAL-STORAGE] Saved: {}", url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file locally", e);
        }
    }

    @Override
    public byte[] download(String objectName) {
        try {
            return Files.readAllBytes(uploadDir.resolve(objectName));
        } catch (IOException e) {
            throw new RuntimeException("File not found: " + objectName, e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(objectName));
        } catch (IOException e) {
            log.warn("[LOCAL-STORAGE] Could not delete {}: {}", objectName, e.getMessage());
        }
    }
}
