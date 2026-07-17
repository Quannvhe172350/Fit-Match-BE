-- BE-15/E-9 (audit 2026-07-17): audit_logs là bảng duy nhất không có Flyway migration
-- (được tạo bởi ddl-auto trong quá khứ) — prod chạy validate sẽ thiếu bảng trên DB mới.
-- Dùng IF NOT EXISTS vì các môi trường dev hiện hữu đã có bảng do Hibernate tạo.
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    action      VARCHAR(64)   NOT NULL,
    target_type VARCHAR(64)   NULL,
    target_id   VARCHAR(64)   NULL,
    description VARCHAR(1000) NULL,
    created_at  DATETIME      NOT NULL,
    updated_at  DATETIME      NULL,
    created_by  VARCHAR(255)  NULL,
    updated_by  VARCHAR(255)  NULL,
    INDEX idx_audit_action (action),
    INDEX idx_audit_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
