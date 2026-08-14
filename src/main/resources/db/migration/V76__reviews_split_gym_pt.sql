-- =====================================================================
-- V76 (P3 — CỘNG THÊM): tách đánh giá thành hai loại (câu 17 + 36).
--
--   target_type = GYM: neo vào VÉ,   mở khi ticket.status = USED_UP
--   target_type = PT : neo vào BUỔI, mở khi session.status = DONE và buổi có PT
--
-- Mỗi vé đúng một đánh giá gym, mỗi buổi đúng một đánh giá PT. UNIQUE trên cột
-- nullable là đủ: InnoDB cho phép NHIỀU dòng NULL trong một unique index, nên
-- không cần partial index (thứ MariaDB không có) — đánh giá PT để ticket_id
-- NULL và ngược lại, hai ràng buộc không đụng nhau.
--
-- booking_id nới thành nullable; các cột catalog cũ (gym_service_id,
-- training_package_id) giữ nguyên tới P4 vì chúng còn FK sang bảng sắp bị drop.
-- =====================================================================

alter table reviews
    modify column booking_id bigint null;

alter table reviews
    add column target_type varchar(10) null,
    add column ticket_id bigint null,
    add column session_id bigint null;

-- Backfill dữ liệu cũ để cột không còn dòng NULL vô nghĩa; toàn bộ review cũ
-- đều là đánh giá phòng gym (mô hình cũ không tách PT).
update reviews set target_type = 'GYM' where target_type is null;

alter table reviews
    add constraint UK_reviews_ticket unique (ticket_id);
alter table reviews
    add constraint UK_reviews_session unique (session_id);

alter table reviews
    add constraint FK_reviews_ticket foreign key (ticket_id) references tickets (id);
alter table reviews
    add constraint FK_reviews_session foreign key (session_id) references training_sessions (id);
