-- =====================================================================
-- V48 (UC-070): báo cáo vấn đề dịch vụ/hành vi — mở rộng ngoài report
-- review (V25). Nhắm tới Gym / PT / Booking; Moderator xử lý (UC-071).
-- Người báo cáo lấy từ created_by (audit).
-- =====================================================================

create table issue_reports (
    id bigint not null auto_increment,
    target_type varchar(20) not null,
    target_id bigint not null,
    reason varchar(1000) not null,
    status varchar(20) not null default 'OPEN',
    moderator_note varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_issue_reports_status (status),
    key idx_issue_reports_target (target_type, target_id)
) engine = InnoDB;
