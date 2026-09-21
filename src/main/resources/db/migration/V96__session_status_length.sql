-- =====================================================================
-- V96: nới cột trạng thái buổi tập cho vừa CANCELLED_BY_CUSTOMER.
--
-- V94 thêm trạng thái CANCELLED_BY_CUSTOMER (21 ký tự) nhưng để nguyên
-- training_sessions.status và session_status_history.from_status/to_status
-- ở varchar(20) (V70/V71). DB chạy STRICT_TRANS_TABLES nên mỗi lần khách huỷ
-- một ngày tập đều rơi vào "Data too long for column" và cả giao dịch (ghi sổ
-- hoàn tiền, chuyển tiền về ví, lịch sử) bị rollback → API /sessions/{id}/cancel
-- trả 500. Chưa từng có buổi nào ở trạng thái này trong DB, nên chỉ nới cột là
-- đủ — không có dữ liệu cần sửa.
--
-- 30 thay vì 21: để lần thêm trạng thái sau không lặp lại đúng lỗi này.
-- =====================================================================

alter table training_sessions
    modify column status varchar(30) not null;

alter table session_status_history
    modify column from_status varchar(30) null,
    modify column to_status varchar(30) not null;
