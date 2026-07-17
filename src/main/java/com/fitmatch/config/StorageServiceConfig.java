package com.fitmatch.config;

import com.fitmatch.service.StorageService;
import com.fitmatch.service.impl.GCSStorageService;
import com.fitmatch.service.impl.LocalStorageService;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageServiceConfig {

    @Bean
    @ConditionalOnBean(Storage.class)
    public StorageService gcsStorageService(
            Storage storage,
            @Value("${app.gcs.bucket}") String bucket) {
        return new GCSStorageService(storage, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService localStorageService(
            @Value("${app.base-url:http://localhost:9898}") String baseUrl,
            @Value("${app.upload-dir:./uploads}") String uploadDir) {
        return new LocalStorageService(baseUrl, uploadDir);
    }
}
