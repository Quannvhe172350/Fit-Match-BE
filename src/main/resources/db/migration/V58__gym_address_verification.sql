-- =====================================================================
-- V58 (bug S2-01): Admin yêu cầu phòng gym xác minh lại địa chỉ khi địa chỉ
-- không đúng chuẩn (thiếu quận/huyện, viết tắt, không geocode được).
--
-- Cố tình KHÔNG dùng verification_status: địa chỉ sai không phải lý do ngắt
-- hoạt động của gym. Hồ sơ cũ mặc định = 1 (coi như đã đạt) để không bắn cảnh
-- báo hàng loạt cho gym đang chạy bình thường.
-- =====================================================================

alter table gym_profiles
    add column address_verified tinyint(1) not null default 1 after review_note,
    add column address_review_note varchar(500) null after address_verified;

insert into notification_templates (code, title, body, placeholders, created_at)
values ('GYM_ADDRESS_RECHECK', 'Cần xác minh lại địa chỉ phòng gym',
        'Địa chỉ "{address}" chưa đạt chuẩn: {note}. Vui lòng cập nhật lại địa chỉ (chọn từ gợi ý bản đồ) để khách tìm đúng phòng gym.',
        '{address}, {note}', utc_timestamp())
on duplicate key update code = code;
