package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Template thông báo chỉnh được bởi Admin (UC-075). Mỗi code ứng với một sự kiện
 * trong NotificationDispatcher; placeholder dạng {key} được thay bằng giá trị
 * runtime. Tắt (enabled=false) hoặc thiếu template -> dùng văn bản mặc định trong
 * code (không bao giờ mất thông báo). Chỉ update qua API — code seed bằng migration.
 */
@Entity
@Table(name = "notification_templates", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_templates_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Danh sách placeholder khả dụng, ví dụ "{bookingId}, {reason}" — hiển thị cho admin. */
    @Column(length = 255)
    private String placeholders;
}
