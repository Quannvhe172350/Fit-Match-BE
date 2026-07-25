-- =====================================================================
-- V49 (UC-078): tái tạo system_configs — KHÁC V6 (bị gỡ ở V40 vì write-only):
-- lần này BE đọc giá trị lúc runtime (DB override, env/default fallback).
-- Quy tắc: CHỈ seed key có code thật sự đọc — không thêm key "để dành".
-- Key đang được đọc:
--   dispute.open-window-days  -> DisputeServiceImpl (cửa sổ mở tranh chấp, UC-063)
-- Admin chỉnh qua PUT /api/admin/system-configs/{key}; không tạo key mới qua API.
-- =====================================================================

create table system_configs (
    id bigint not null auto_increment,
    config_key varchar(100) not null,
    config_value varchar(255) not null,
    description varchar(255) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_system_configs_key (config_key)
) engine = InnoDB;

insert into system_configs (config_key, config_value, description, created_at)
values ('dispute.open-window-days', '14',
        'Số ngày được mở tranh chấp kể từ khi buổi tập kết thúc/booking bị hủy (UC-063)',
        utc_timestamp());
