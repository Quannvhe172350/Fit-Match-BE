-- =====================================================================
-- V75 (P3 — CỘNG THÊM): tranh chấp neo vào vé, và tuỳ chọn neo thêm vào một
-- buổi tập cụ thể (câu 34).
--
--   ticket_id + session_id NULL     -> tranh chấp CẤP VÉ (vd gym đóng cửa)
--   ticket_id + session_id NOT NULL -> tranh chấp CẤP BUỔI (vd PT không đến)
--
-- Số tiền của tranh chấp cấp buổi = payableAmount / dayCount (giá trị đầy đủ
-- một ngày tập), theo lựa chọn đã chốt khi duyệt kế hoạch.
-- =====================================================================

alter table disputes
    modify column booking_id bigint null;

alter table disputes
    add column ticket_id bigint null,
    add column session_id bigint null;

create index idx_disputes_ticket on disputes (ticket_id);
create index idx_disputes_session on disputes (session_id);

alter table disputes
    add constraint FK_disputes_ticket foreign key (ticket_id) references tickets (id);
alter table disputes
    add constraint FK_disputes_session foreign key (session_id) references training_sessions (id);
