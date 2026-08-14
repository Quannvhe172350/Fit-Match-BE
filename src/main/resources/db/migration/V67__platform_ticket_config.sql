-- =====================================================================
-- V67 (mô hình vé — câu 32): hạn sử dụng vé do Admin cấu hình.
-- Dùng mẫu "một dòng hiệu lực" giống commission_configs: luôn đọc bản mới
-- nhất, mỗi lần Admin đổi thì ghi đè dòng đó (lịch sử nằm ở audit_logs).
--
-- Số ngày ở đây chỉ dùng để CHỐT expires_at tại thời điểm mua vé — vé đã bán
-- giữ nguyên hạn cũ khi Admin đổi cấu hình (snapshot, không đọc động).
-- =====================================================================

create table platform_ticket_config (
    id bigint not null auto_increment,
    day_ticket_expiry_days int not null,
    package_ticket_expiry_days int not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

insert into platform_ticket_config
    (day_ticket_expiry_days, package_ticket_expiry_days, created_at, created_by)
values (30, 90, utc_timestamp(), 'system');
