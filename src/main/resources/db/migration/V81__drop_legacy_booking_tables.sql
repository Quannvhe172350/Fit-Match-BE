-- =====================================================================
-- V81 (P4 — ⚠️ DROP BẢNG, KHÔNG PHỤC HỒI ĐƯỢC): gỡ toàn bộ mô hình booking.
--
-- Chạy SAU V79 (dữ liệu đã sạch) nên không có dòng nào bị mất ngoài dự kiến.
-- Thứ tự: gỡ cột/khoá ngoại còn trỏ tới các bảng sắp drop, rồi drop từ con
-- lên cha.
--
-- Sau migration này, `ddl-auto: validate` chính là cổng nghiệm thu tự động:
-- app boot được nghĩa là không còn entity nào tham chiếu schema đã chết.
--
-- MỌI CÂU Ở ĐÂY ĐỀU IDEMPOTENT (`if exists`). MariaDB không có DDL trong
-- transaction: một migration drop mà chết giữa chừng sẽ để lại schema nửa vời
-- và Flyway không thể rollback. Không có `if exists`, lần chạy thứ hai sẽ chết
-- ở câu đầu tiên đã áp dụng xong ⇒ schema kẹt vĩnh viễn, chỉ còn cách drop DB.
-- Với `if exists`, sau khi `flyway repair` xoá dòng failed là chạy lại được.
-- =====================================================================

-- ---------- 1. Gỡ neo cũ khỏi các bảng ĐƯỢC GIỮ LẠI ----------

alter table if exists reviews
    drop foreign key if exists fk_reviews_booking;
alter table if exists reviews
    drop foreign key if exists fk_reviews_service;
alter table if exists reviews
    drop foreign key if exists fk_reviews_package;
alter table if exists reviews
    drop column if exists booking_id,
    drop column if exists gym_service_id,
    drop column if exists training_package_id;

alter table if exists disputes
    drop foreign key if exists fk_disputes_booking;
alter table if exists disputes
    drop column if exists booking_id;

alter table if exists refund_requests
    drop foreign key if exists FK_refund_booking;
alter table if exists refund_requests
    drop column if exists booking_id;

-- ---------- 2. Drop bảng theo thứ tự khoá ngoại ----------

-- Câu 19: ảnh bằng chứng chuyển sang training_sessions.evidence_url.
drop table if exists session_notes;
drop table if exists booking_status_history;

-- Câu 16: bỏ hàng chờ.
drop table if exists waitlist_entries;

-- bookings và customer_packages tham chiếu VÒNG TRÒN nhau:
--   customer_packages.purchase_booking_id -> bookings   (fk_cp_booking, V24)
--   bookings.customer_package_id -> customer_packages   (fk_bookings_customer_package, V24)
-- Nên không bảng nào drop trước được — phải cắt một chiều trước đã.
alter table if exists bookings
    drop foreign key if exists fk_bookings_customer_package;

-- Vé gói giờ là Ticket + training_sessions, không cần entity đếm buổi riêng.
drop table if exists customer_packages;

drop table if exists bookings;

-- Câu 26: lịch PT theo ngày cụ thể ⇒ "bận" = không khai khung giờ.
-- Hai bảng này thành thừa hoàn toàn.
drop table if exists blocked_times;
drop table if exists availability_slots;

-- Quyết định #1: catalog thay toàn phần bằng ticket_types.
-- booking_rules là @Embeddable (cột nằm trên hai bảng này) nên biến mất theo.
drop table if exists training_packages;
drop table if exists gym_services;
