-- =====================================================================
-- V44 (P0-0.7, UC-057/059/060/062): defense-in-depth cho ví.
-- Chống ghi số dư ÂM ở tầng DB — bổ sung cho guard tầng app (WalletService.require
-- + pessimistic lock). Nếu một code path mới bỏ sót kiểm tra, CHECK constraint chặn
-- ngay thay vì để ví về âm âm thầm.
-- MariaDB 10.2+ thực thi CHECK constraint.
-- =====================================================================
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_available_nonneg CHECK (available_balance >= 0),
    ADD CONSTRAINT chk_wallet_frozen_nonneg    CHECK (frozen_balance   >= 0),
    ADD CONSTRAINT chk_wallet_held_nonneg      CHECK (held_balance     >= 0),
    ADD CONSTRAINT chk_wallet_pending_nonneg   CHECK (pending_balance  >= 0);
