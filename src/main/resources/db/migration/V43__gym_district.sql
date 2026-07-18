-- =====================================================================
-- V43 (UC-18/bug 11): thêm cột district (quận/huyện) cho gym_profiles và
-- gym_branches — FE đã thu thập district từ form đăng ký nhưng BE bỏ rơi,
-- và bộ lọc marketplace cần lọc theo thành phố + quận.
-- =====================================================================

alter table gym_profiles
    add column district varchar(100) null after city;

alter table gym_branches
    add column district varchar(100) null after city;
