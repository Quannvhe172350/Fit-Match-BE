-- =====================================================================
-- V28 (UC-073): voucher/khuyến mãi và liên kết với booking.
-- =====================================================================

create table vouchers (
    id bigint not null auto_increment,
    code varchar(40) not null,
    description varchar(255) null,
    discount_type varchar(10) not null,
    discount_value decimal(12,2) not null,
    min_booking_amount decimal(12,2) null,
    max_discount decimal(12,2) null,
    usage_limit int null,
    used_count int not null default 0,
    valid_from datetime(6) null,
    valid_to datetime(6) null,
    active bit not null default 1,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_vouchers_code (code)
) engine = InnoDB;

alter table bookings
    add column voucher_id bigint null,
    add column discount_amount decimal(12,2) null,
    add constraint fk_bookings_voucher foreign key (voucher_id) references vouchers (id);
