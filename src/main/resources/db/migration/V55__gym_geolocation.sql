-- =====================================================================
-- V55 (UC-18): toạ độ địa lý cho tìm kiếm phòng gym theo bán kính.
--
-- Bộ lọc marketplace trước đây chỉ so khớp city/district dạng text nên không
-- trả lời được câu hỏi "gym nào quanh tôi 5km". Thêm lat/lng cho CẢ hồ sơ gym
-- (trụ sở) lẫn từng chi nhánh: một chuỗi gym có thể có trụ sở ở Cầu Giấy nhưng
-- chi nhánh ngay cạnh người dùng — khoảng cách hiển thị là điểm gần nhất.
--
-- place_id / formatted_address là kết quả Google Geocoding API được lưu lại để
-- tránh geocode lại địa chỉ không đổi; geocoded_at cho biết dữ liệu đã cũ chưa.
-- =====================================================================

alter table gym_profiles
    add column latitude decimal(10, 7) null after district,
    add column longitude decimal(10, 7) null after latitude,
    add column place_id varchar(255) null after longitude,
    add column formatted_address varchar(500) null after place_id,
    add column geocoded_at datetime null after formatted_address;

alter table gym_branches
    add column latitude decimal(10, 7) null after district,
    add column longitude decimal(10, 7) null after latitude,
    add column place_id varchar(255) null after longitude,
    add column formatted_address varchar(500) null after place_id,
    add column geocoded_at datetime null after formatted_address;

-- Truy vấn bán kính lọc trước bằng bounding box (latitude BETWEEN ... AND
-- longitude BETWEEN ...) rồi mới tính haversine, nên index kép theo đúng thứ tự đó.
create index idx_gym_profiles_lat_lng on gym_profiles (latitude, longitude);
create index idx_gym_branches_lat_lng on gym_branches (latitude, longitude);
