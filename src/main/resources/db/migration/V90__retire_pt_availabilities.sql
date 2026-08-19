-- =====================================================================
-- V90 (quyết định §4.5): gỡ pt_availabilities khỏi mô hình.
--
-- KHÔNG backfill thành ca. Khung giờ PT tự khai là dữ liệu tự do (mỗi PT một
-- kiểu, 06:15-07:45 không map được vào ca nào), suy ngược sẽ sinh ra hàng chục
-- "ca" rác cấp chi nhánh — đúng thứ mô hình mới muốn xoá bỏ.
--
-- RENAME thay vì DROP: giữ một sprint để Gym còn tra được PT trước đây hay dạy
-- khung nào khi ngồi xếp ca lần đầu. V91 (đợt sau) mới drop hẳn. Hibernate
-- ddl-auto=validate không đụng tới bảng không được entity nào map, nên bảng
-- legacy nằm lại là vô hại.
--
-- CẢNH BÁO VẬN HÀNH: mọi buổi SCHEDULED tương lai đang không có ca nào phủ sẽ
-- không dời lịch được (PtSlotValidator từ chối) và PT của buổi đó không hiện
-- trong lưới chọn PT, cho tới khi Gym xếp ca phủ đúng khung giờ ấy. Câu lệnh
-- select bên dưới in ra đúng danh sách cần xử lý trước khi mở luồng mới.
-- =====================================================================

rename table pt_availabilities to pt_availabilities_legacy_v90;

-- Không đổi dữ liệu — chỉ để lại dấu vết trong log của Flyway khi chạy tay:
--   select s.gym_branch_id, s.pt_profile_id, s.session_date, s.pt_slot_start
--     from training_sessions s
--    where s.status = 'SCHEDULED'
--      and s.pt_profile_id is not null
--      and s.session_date >= curdate()
--    order by s.gym_branch_id, s.session_date, s.pt_slot_start;
