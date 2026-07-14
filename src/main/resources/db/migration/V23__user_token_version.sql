-- =====================================================================
-- V23 (UC-003/004): phiên bản token trên user — vô hiệu hoá JWT cũ khi
-- logout / đổi / reset mật khẩu (token cũ mang ver thấp hơn sẽ bị từ chối).
-- =====================================================================

alter table users
    add column token_version int not null default 0;
