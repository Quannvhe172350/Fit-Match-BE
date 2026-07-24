-- =====================================================================
-- V45 (P1-1.6, UC-003): auto-lockout đăng nhập.
-- Đếm số lần đăng nhập sai liên tiếp theo TÀI KHOẢN (bổ sung cho rate-limit
-- theo IP vốn có) và khóa tạm khi vượt ngưỡng — chống brute-force mật khẩu.
-- =====================================================================
ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN lockout_until DATETIME(6) NULL;
