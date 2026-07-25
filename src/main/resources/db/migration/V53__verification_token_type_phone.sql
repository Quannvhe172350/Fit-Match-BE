-- =====================================================================
-- V53 (UC-002, fix phát hiện qua E2E): cột verification_tokens.type là
-- ENUM (V1 baseline) — V50 thêm TokenType.PHONE_VERIFICATION ở Java nhưng
-- quên mở rộng ENUM => insert OTP bị "Data truncated for column 'type'".
-- (Cùng lớp lỗi với V31 wallet_transactions.type; FlywayMigrationTest đã
-- có guard cho wallet enum — bổ sung guard tương tự cho token type.)
-- =====================================================================

alter table verification_tokens
    modify column type enum ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'PHONE_VERIFICATION') not null;
