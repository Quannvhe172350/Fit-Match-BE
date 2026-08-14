-- =====================================================================
-- V77 (P4 — ⚠️ PHÁ HUỶ, KHÔNG REVERT ĐƯỢC BẰNG CODE): gỡ neo booking khỏi
-- ba bảng dòng tiền. Từ đây payment_orders/wallet_transactions/
-- loyalty_transactions chỉ còn biết tới VÉ.
--
-- BẮT BUỘC dump DB trước khi chạy toàn bộ P4 (rủi ro R3).
--
-- Thứ tự trong P4:
--   V77 gỡ neo tiền  ->  V78 pt_assignments  ->  V79 xoá dữ liệu
--   ->  V80 reset ví  ->  V81 drop bảng cũ
--
-- Đây là lý do P3+P4 phải deploy CHUNG một cửa sổ downtime (R1): sau
-- migration này, code của luồng booking cũ không chạy được nữa, và vì
-- ddl-auto: validate nên entity còn tham chiếu cột đã drop là app không boot.
-- =====================================================================

alter table payment_orders
    drop foreign key FK_payment_orders_booking;
alter table payment_orders
    drop column booking_id;

-- Hai cột dưới chỉ là tham chiếu mô tả (không có FK), drop thẳng.
alter table wallet_transactions
    drop column booking_id;

alter table loyalty_transactions
    drop column booking_id;

-- V76 đã backfill 'GYM' cho dữ liệu cũ nên siết được NOT NULL: từ đây mọi
-- đánh giá đều phải nói rõ nó chấm điểm phòng gym hay chấm điểm PT.
alter table reviews
    modify column target_type varchar(10) not null;
