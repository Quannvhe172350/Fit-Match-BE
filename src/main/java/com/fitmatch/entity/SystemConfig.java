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
 * Tham số hệ thống chỉnh runtime (UC-078). Khác system_configs cũ (V6, gỡ ở V40
 * vì write-only): mỗi key ở đây PHẢI có code đọc thật — không tạo key mới qua API,
 * chỉ update giá trị của key đã seed bằng migration.
 */
@Entity
@Table(name = "system_configs", uniqueConstraints =
        @UniqueConstraint(name = "uk_system_configs_key", columnNames = "config_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(length = 255)
    private String description;
}
