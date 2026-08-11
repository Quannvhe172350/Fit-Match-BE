-- =====================================================================
-- V63 (UC-18): phân biệt "toạ độ do Google đoán" với "toạ độ do người sửa tay".
--
-- Form địa chỉ giờ có bản đồ kéo được ghim: Google hay đặt ghim ở giữa lô đất
-- hoặc sai cổng vào, chủ gym kéo lại cho đúng. Không có cờ này thì job làm mới
-- của V62 (tra theo place_id mỗi 180 ngày) sẽ lặng lẽ kéo ghim về chỗ Google
-- nói, xoá sạch công sửa tay — và không ai hiểu vì sao ghim tự nhảy.
--
-- place_id KHÔNG dùng làm cờ thay thế được: một bản ghi vừa có place_id (chọn từ
-- gợi ý Places) vừa bị kéo ghim là trường hợp bình thường nhất.
-- =====================================================================

alter table gym_profiles
    add column coordinates_pinned tinyint(1) not null default 0 after location_type;

alter table gym_branches
    add column coordinates_pinned tinyint(1) not null default 0 after location_type;
