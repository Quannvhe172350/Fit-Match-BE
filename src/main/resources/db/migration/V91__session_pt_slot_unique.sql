-- =====================================================================
-- V91 (edge case §7.9): chốt chặn CHỐNG ĐẶT TRÙNG ở tầng DB.
--
-- Trước đây "một slot một khách" chỉ được bảo đảm bằng kiểm tra trong
-- PtSlotValidator.isTaken() — hai request đồng thời cùng đọc "chưa ai đặt" rồi
-- cùng ghi. @Version của training_sessions chống lost-update trên MỘT dòng,
-- không chống được hai dòng MỚI cùng chiếm một slot.
--
-- MySQL/MariaDB cho phép trùng NULL trong unique index, nên buổi tự tập
-- (pt_profile_id null, pt_slot_start null) không bị ràng buộc này chạm tới.
--
-- Nếu migration này FAIL vì trùng khoá: đó là dữ liệu đặt trùng có thật đã lọt
-- qua từ trước — phải xem và xử lý tay, KHÔNG được nới ràng buộc để chạy qua.
-- =====================================================================

create unique index uk_session_pt_slot
    on training_sessions (pt_profile_id, session_date, pt_slot_start);
