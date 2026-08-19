package com.fitmatch.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Edge case §7.8: chốt múi giờ ứng dụng về Asia/Ho_Chi_Minh.
 *
 * <p>Trước V85 không có chỗ nào khai múi giờ — JVM lấy múi giờ của máy chủ. Với
 * lịch theo NGÀY thuần ({@code session_date}, {@code work_date}) thì lệch múi
 * giờ đã đủ để {@code LocalDate.now()} trả sai ngày quanh nửa đêm. Từ V85 nó
 * còn quyết cả tiền: ràng buộc "PT phải báo nghỉ trước N giờ" so
 * {@code LocalDateTime.now()} với giờ bắt đầu buổi tập, và lệch bảy tiếng ở đó
 * nghĩa là chặn nhầm hoặc cho qua nhầm một đơn có hệ quả hoàn tiền.
 *
 * <p>Đặt ở JVM thay vì sửa từng chỗ gọi: mọi {@code LocalDate.now()} sẵn có
 * (hoàn tiền, hết hạn vé, tranh chấp) cũng cần đúng múi giờ như nhau, và sửa
 * rải rác chắc chắn sẽ sót.
 */
@Slf4j
@Configuration
public class TimeZoneConfig {

    public static final String APP_ZONE = "Asia/Ho_Chi_Minh";

    @PostConstruct
    public void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_ZONE));
        log.info("Application timezone fixed to {}", APP_ZONE);
    }
}
