-- =====================================================================
-- V73 (P2 — CỘNG THÊM, chưa phá gì): các bảng dòng tiền neo được vào vé.
--
-- Ở giai đoạn này hai luồng chạy song song nên payment_orders phải nhận cả
-- booking_id (luồng cũ) lẫn ticket_id (luồng vé). booking_id vì thế được nới
-- thành nullable; ràng buộc "đúng một trong hai" do service layer giữ, vì
-- MariaDB 10.x không kiểm được CHECK có subquery và ta cần rollback được bằng
-- cách revert code.
--
-- wallet_transactions và loyalty_transactions cũng nhận thêm ticket_id. Hai cột
-- booking_id ở đó chỉ là tham chiếu mô tả (không có FK), nên về lý thuyết có
-- thể nhét id vé vào — nhưng khi hai luồng cùng chạy thì booking #5 và vé #5
-- trông y hệt nhau trong sổ cái. Thêm cột riêng là cách duy nhất để đối soát ở
-- P2 vẫn đọc được.
--
-- P3 sẽ drop hẳn ba cột booking_id khi luồng cũ chết. Đây là lý do migration
-- này nằm ở P2 chứ không ở P3: giữ đúng tính chất additive để P1-P2 rollback
-- được bằng cách revert code.
--
-- ref_code vẫn giữ nguyên dạng FM<id><6 hex> — PaymentWebhookServiceImpl dò
-- nội dung chuyển khoản bằng regex FM\d+[0-9A-F]{6}, đổi tiền tố là Casso
-- không khớp được giao dịch nào nữa.
-- =====================================================================

alter table payment_orders
    modify column booking_id bigint null;

alter table payment_orders
    add column ticket_id bigint null;

alter table payment_orders
    add constraint UK_payment_orders_ticket unique (ticket_id);

alter table payment_orders
    add constraint FK_payment_orders_ticket foreign key (ticket_id) references tickets (id);

-- Sổ cái ví: truy vết escrow theo vé.
alter table wallet_transactions
    add column ticket_id bigint null;

create index idx_wallet_txn_ticket on wallet_transactions (ticket_id);

-- Sổ cái điểm thưởng: tích/tiêu/hoàn theo vé.
alter table loyalty_transactions
    add column ticket_id bigint null;

create index idx_loyalty_txn_ticket on loyalty_transactions (ticket_id);
