-- =====================================================================
-- V18 (UC-072): Cấu hình kinh tế nền tảng (hoa hồng, phí, holding period).
-- Seed một dòng mặc định: hoa hồng 15%, phí 0%, giữ tiền 3 ngày.
-- =====================================================================

create table commission_configs (
    commission_percent decimal(5,2) not null,
    platform_fee_percent decimal(5,2) not null,
    settlement_hold_days integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

insert into commission_configs
    (commission_percent, platform_fee_percent, settlement_hold_days, created_at, created_by)
values (15.00, 0.00, 3, now(6), 'system');
