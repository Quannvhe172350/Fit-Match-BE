-- =====================================================================
-- V93: độ dài buổi tập ghi trên vé — "mỗi ngày X giờ".
--
-- Trước đây độ dài một buổi HOÀN TOÀN do ca của gym quyết (gym_shifts.slot_minutes):
-- vé chỉ nói số NGÀY, không nói mỗi ngày tập bao lâu. Gym bán "gói 10 buổi 90
-- phút" thì con số 90 phút không nằm ở đâu trong hệ thống, và khách đặt nhằm ca
-- 60 phút cũng không có gì chặn.
--
-- NULL = giữ nguyên hành vi cũ: nhận mọi ca, độ dài do ca quyết. Vé đã bán trước
-- migration này đều NULL nên không vé nào đổi luật giữa chừng.
--
-- Ghi cả ở tickets vì vé là SNAPSHOT lúc mua (giống price, day_count,
-- pt_surcharge_per_day, expires_at): gym sửa loại vé sau đó không được phép đổi
-- luật của những tấm vé đã bán.
--
-- Đơn vị PHÚT, không phải giờ: ca 90 phút là chuyện thường, lưu giờ thì phải
-- dùng số thập phân cho một đại lượng vốn luôn nguyên.
-- =====================================================================

alter table ticket_types
    add column minutes_per_day int null after day_count;

alter table tickets
    add column minutes_per_day int null after day_count;
