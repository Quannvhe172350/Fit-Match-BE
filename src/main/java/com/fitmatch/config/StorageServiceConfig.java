package com.fitmatch.config;

import com.fitmatch.service.StorageService;
import com.fitmatch.service.impl.GCSStorageService;
import com.fitmatch.service.impl.LocalStorageService;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chọn nơi lưu file: bucket GCS hay đĩa của tiến trình đang chạy.
 *
 * <p>Điều kiện đặt trên CHÍNH cờ {@code app.gcs.enabled}, không phải trên sự tồn
 * tại của bean {@code Storage}. {@code @ConditionalOnBean} phụ thuộc thứ tự đăng
 * ký bean giữa các lớp {@code @Configuration} — thứ tự đó không được bảo đảm, và
 * Spring khuyến cáo chỉ dùng nó trong auto-configuration. Khi nó đánh giá trượt,
 * hệ thống KHÔNG báo lỗi mà lặng lẽ rơi về đĩa: file ghi vào container rồi mất
 * sạch sau redeploy, còn DB thì đã lưu URL {app.base-url}/api/media/raw/... và
 * không ai biết chuyện gì đã xảy ra cho tới khi ảnh vỡ hàng loạt.
 *
 * <p>Vì lẽ đó cả hai nhánh đều ghi log lúc khởi động: đọc log là biết ngay đang
 * dùng gì, thay vì phải suy ra từ hình dạng URL trong DB.
 */
@Slf4j
@Configuration
public class StorageServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "app.gcs.enabled", havingValue = "true")
    public StorageService gcsStorageService(
            Storage storage,
            @Value("${app.gcs.bucket}") String bucket,
            @Value("${app.media.public-read:true}") boolean publicRead) {
        log.info("Storage: Google Cloud Storage, bucket '{}' (public-read={})", bucket, publicRead);
        return new GCSStorageService(storage, bucket, publicRead);
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService localStorageService(
            @Value("${app.base-url:http://localhost:9898}") String baseUrl,
            @Value("${app.upload-dir:./uploads}") String uploadDir) {
        log.warn("Storage: ĐĨA CỤC BỘ tại '{}' (app.gcs.enabled không bật). "
                + "File sẽ mất khi tiến trình bị thay thế và không chia sẻ được giữa "
                + "nhiều instance — chỉ dùng cho máy dev.", uploadDir);
        return new LocalStorageService(baseUrl, uploadDir);
    }
}
