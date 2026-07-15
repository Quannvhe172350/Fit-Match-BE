-- =====================================================================
-- V32 (UC-066/067): optimistic lock cho disputes.
-- Chống hai lệnh resolve chạy song song cùng áp tài chính trên cùng
-- frozen_amount (double refund/release, rút held của booking khác trong
-- ví Gym). Cột version do JPA @Version quản lý; lệnh commit sau cùng gặp
-- version cũ -> OptimisticLockException -> rollback cả bút toán ví.
-- =====================================================================
ALTER TABLE disputes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
