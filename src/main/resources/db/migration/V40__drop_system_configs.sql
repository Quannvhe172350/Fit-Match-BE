-- E-8 (quyết định nghiệp vụ 2026-07-17): gỡ system_configs — kho key-value write-only,
-- không logic BE nào đọc từ khi tạo (V6). Tham số vận hành thật nằm ở commission_configs
-- (UC-072) và booking_rules per-service/package (UC-026).
DROP TABLE IF EXISTS system_configs;
