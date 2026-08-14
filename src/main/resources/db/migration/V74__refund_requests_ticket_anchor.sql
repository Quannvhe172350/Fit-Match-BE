-- =====================================================================
-- V74 (P3 — CỘNG THÊM): yêu cầu hoàn tiền neo vào vé.
--
-- Vẫn additive như V73: booking_id nới thành nullable, thêm ticket_id. Lý do
-- không drop ngay là ddl-auto: validate — drop cột trong khi entity Booking và
-- toàn bộ luồng cũ còn sống thì app không boot được. P4 drop, cùng lúc với
-- việc xoá code cũ, trong một cửa sổ downtime duy nhất (rủi ro R1).
--
-- refund_mode      : FULL | PARTIAL_ELAPSED — lựa chọn của admin (câu 11)
-- elapsed_days     : số ngày đã dùng tại thời điểm duyệt, lưu để đối soát về sau
-- retained_amount  : phần giữ lại cho gym; luôn thoả retained + amount_refunded
--                    = payable của vé (PartialRefundCalculator bảo đảm)
-- =====================================================================

alter table refund_requests
    modify column booking_id bigint null;

alter table refund_requests
    add column ticket_id bigint null,
    add column refund_mode varchar(20) null,
    add column elapsed_days int null,
    add column retained_amount decimal(14,2) null;

create index idx_refund_ticket on refund_requests (ticket_id);

alter table refund_requests
    add constraint FK_refund_ticket foreign key (ticket_id) references tickets (id);
