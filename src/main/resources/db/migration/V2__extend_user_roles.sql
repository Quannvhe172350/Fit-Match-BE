-- =====================================================================
-- V2 (UC-077): Bổ sung role MODERATOR và FINANCE_ADMIN cho mô hình
-- phân quyền mới (Moderator xử lý dispute/moderation, Finance Admin
-- xử lý settlement/refund/payout).
-- MariaDB ENUM: mở rộng danh sách bằng MODIFY COLUMN; giá trị cũ được
-- giữ nguyên theo khớp chuỗi nên không ảnh hưởng dữ liệu hiện có.
-- =====================================================================

alter table users
    modify column role enum (
        'ROLE_ADMIN',
        'ROLE_CUSTOMER',
        'ROLE_FINANCE_ADMIN',
        'ROLE_GYM_OPERATOR',
        'ROLE_MODERATOR',
        'ROLE_PT'
    ) not null;
