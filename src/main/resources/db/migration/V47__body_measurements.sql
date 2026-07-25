-- =====================================================================
-- V47 (UC-051): số đo cơ thể — khách hàng tự ghi nhận để theo dõi tiến
-- trình tập luyện (kết hợp với lịch sử booking/ghi chú buổi tập/gói tập).
-- =====================================================================

create table body_measurements (
    id bigint not null auto_increment,
    user_id bigint not null,
    measured_at date not null,
    weight_kg decimal(5, 2) null,
    height_cm decimal(5, 2) null,
    body_fat_percent decimal(4, 1) null,
    chest_cm decimal(5, 2) null,
    waist_cm decimal(5, 2) null,
    hip_cm decimal(5, 2) null,
    note varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_body_measurements_user (user_id, measured_at),
    constraint fk_body_measurements_user foreign key (user_id) references users (id)
) engine = InnoDB;
