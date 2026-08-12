-- =====================================================================
-- V65 (UC-18): ghi nhận dịch vụ nào đã cấp place_id đang lưu.
--
-- Hệ thống không còn khoá cứng vào Google: app.geocoding.provider chọn giữa
-- GOOGLE / GEOAPIFY / NOMINATIM. Nhưng place_id của ba dịch vụ là ba không gian
-- định danh KHÁC HẲN nhau, mà không cái nào báo lỗi khi nhận id của cái kia —
-- chúng chỉ đơn giản trả về một địa điểm khác.
--
-- Hậu quả nếu thiếu cột này: job làm mới định kỳ (mỗi 180 ngày, tra theo
-- place_id) sẽ đem id của Google đi hỏi Geoapify, nhận về một địa điểm ngẫu
-- nhiên, rồi lặng lẽ dời ghim phòng gym sang đó. Không log lỗi, không ai biết,
-- cho tới khi khách đi tập lạc đường.
--
-- geocode_cache KHÔNG cần cột tương ứng: khoá đệm đã được gắn tiền tố tên
-- provider (xem CachingGeocodingService#providerScoped), nên bản ghi của dịch vụ
-- cũ không bao giờ khớp nữa và tự hết hạn theo TTL.
-- =====================================================================

alter table gym_profiles
    add column place_provider varchar(20) null after place_id;

alter table gym_branches
    add column place_provider varchar(20) null after place_id;

-- Mọi place_id đang có trong DB đều do Google cấp (đó là dịch vụ duy nhất từng
-- chạy). Gắn nhãn để job làm mới nhận ra và bỏ qua khi đã đổi sang provider khác.
update gym_profiles set place_provider = 'GOOGLE' where place_id is not null;
update gym_branches  set place_provider = 'GOOGLE' where place_id is not null;
