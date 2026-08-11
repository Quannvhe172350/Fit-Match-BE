-- =====================================================================
-- V59 (UC-18): giảm chi phí Google + tự phát hiện địa chỉ kém chất lượng.
--
-- 1) geocode_cache: cùng một chuỗi địa chỉ được geocode lại rất nhiều lần
--    (5 chi nhánh cùng toà nhà, backfill chạy lặp, operator lưu đi lưu lại).
--    Mỗi lần là một lượt gọi tính tiền cho một câu trả lời không đổi.
--
-- 2) location_type: Google cho biết kết quả chính xác tới đâu (ROOFTOP = đúng
--    toà nhà, APPROXIMATE = chỉ tới tâm phường/quận). Lưu lại để tự bật cờ
--    address_verified của V58 thay vì chờ Admin soát tay từng hồ sơ.
-- =====================================================================

create table geocode_cache (
    -- SHA-256 của chuỗi truy vấn đã chuẩn hoá. Dùng hash làm khoá thay vì chính
    -- chuỗi địa chỉ: địa chỉ dài hơn giới hạn index của MySQL và có collation
    -- tiếng Việt khiến "Hà Nội" với "HA NOI" so khớp lẫn nhau ngoài ý muốn.
    -- varchar chứ không phải char: Hibernate ddl-auto=validate ánh xạ String sang
    -- varchar, gặp char(64) sẽ báo lệch kiểu và chặn khởi động ứng dụng.
    query_hash        varchar(64)  not null primary key,
    -- Giữ nguyên chuỗi gốc để debug khi kết quả cache sai.
    query             varchar(500) not null,
    latitude          decimal(10, 7) null,
    longitude         decimal(10, 7) null,
    place_id          varchar(255) null,
    formatted_address varchar(500) null,
    location_type     varchar(30)  null,
    cached_at         datetime     not null,
    -- Dọn bản ghi quá hạn theo cached_at.
    key idx_geocode_cache_cached_at (cached_at)
);

alter table gym_profiles
    add column location_type varchar(30) null after formatted_address;

alter table gym_branches
    add column location_type varchar(30) null after formatted_address;

-- Dò hồ sơ trùng địa điểm (nhiều gym khai cùng một place_id = dấu hiệu khai khống)
-- và là điều kiện lọc của job làm mới toạ độ định kỳ.
create index idx_gym_profiles_place_id on gym_profiles (place_id);
create index idx_gym_branches_place_id on gym_branches (place_id);
