-- =====================================================================
-- V50 (UC-002): xác minh số điện thoại bằng OTP.
-- - users.phone_verified: cờ đã xác minh SĐT (reset khi đổi SĐT).
-- - verification_tokens.attempts: đếm số lần nhập sai OTP (khóa sau 5 lần,
--   chống brute-force mã 6 chữ số).
-- Kênh gửi SMS cắm sau qua SmsService (hiện LoggingSmsService — log thay vì gửi,
-- cùng pattern LoggingEmailService).
-- =====================================================================

alter table users
    add column phone_verified tinyint(1) not null default 0;

alter table verification_tokens
    add column attempts int not null default 0;
