-- =====================================================================
-- V54 (UC-053/056): biến payment_transactions từ bảng write-only thành hàng
-- đợi đối soát. Trước đây tiền vào tài khoản nền tảng mà không khớp booking
-- (sai nội dung CK, thiếu/thừa tiền, vào sau khi đơn hết hạn, CK trùng) chỉ để
-- lại một dòng log.warn — không API, không màn hình, không ai xử lý được.
--
-- recon_status: APPLIED | NEEDS_REVIEW | RESOLVED_APPLIED | RESOLVED_REFUNDED
--               | RESOLVED_IGNORED
-- anomaly:      UNMATCHED | UNDERPAID | OVERPAID | LATE_ARRIVAL | DUPLICATE
-- =====================================================================

alter table payment_transactions
    add column recon_status varchar(20) not null default 'NEEDS_REVIEW',
    add column anomaly varchar(20) null,
    add column resolution_note varchar(500) null,
    add column resolved_by varchar(100) null,
    add column resolved_at datetime(6) null;

-- Backfill dữ liệu cũ: đã gắn được đơn -> coi như đã áp xong (APPLIED); còn
-- lại là giao dịch không khớp refCode, đúng bản chất UNMATCHED cần Finance xem.
update payment_transactions
set recon_status = 'APPLIED'
where payment_order_id is not null;

update payment_transactions
set recon_status = 'NEEDS_REVIEW',
    anomaly      = 'UNMATCHED'
where payment_order_id is null;

-- Default chỉ để backfill an toàn; từ đây BE luôn set giá trị tường minh.
alter table payment_transactions
    alter column recon_status drop default;

create index idx_paytxn_recon on payment_transactions (recon_status);
