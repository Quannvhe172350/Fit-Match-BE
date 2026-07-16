-- =====================================================================
-- V36 (P1 batch 2): optimistic lock cho các entity ghi tiền/trạng thái còn
-- thiếu @Version (ngoài bookings V33 và disputes V32). Chống lost-update /
-- double-apply khi hai luồng thao tác đồng thời trên cùng bản ghi:
--   customer_packages : double-consume buổi cuối khi 2 booking cùng gói
--                       hoàn tất đồng thời (P1-6 phần completion).
--   refund_requests   : hai admin cùng approve/execute -> double refund.
--   withdrawal_requests: hai admin cùng mark-paid -> double payout.
--   payment_orders    : hai webhook khác external_id cùng khớp -> double hold.
--   gym_profiles      : hai admin approve vs reject ghi đè quyết định.
-- Cột version do JPA @Version quản lý; lệnh commit sau gặp version cũ ->
-- OptimisticLockException -> rollback (GlobalExceptionHandler map thành 409).
-- =====================================================================
ALTER TABLE customer_packages   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refund_requests     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE withdrawal_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_orders      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE gym_profiles        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
