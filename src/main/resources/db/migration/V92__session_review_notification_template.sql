-- =====================================================================
-- V92: mẫu thông báo mời đánh giá HLV sau khi buổi tập hoàn thành (câu 36).
--
-- Đánh giá PT mở theo TỪNG buổi (khác đánh giá gym: mở khi dùng hết vé, một
-- lần cho cả vé), nhưng trước đây không có thông báo nào ở mốc đó — khách phải
-- tự vào lịch mới biết mình được đánh giá.
--
-- Văn bản trong NotificationDispatcher là mặc định; hàng này để admin sửa được
-- qua /api/admin/notification-templates. Placeholders phải khớp map vars của
-- sessionDoneReviewPt, nếu không thì chỗ thay thế để trống.
-- =====================================================================

insert into notification_templates (code, title, body, placeholders, created_at) values
    ('SESSION_DONE_REVIEW_PT', 'Đánh giá HLV buổi vừa tập?',
     'Buổi ngày {date} với HLV {ptName} đã hoàn thành. Chấm điểm buổi tập này để HLV và người tập sau đều biết mình đang chọn ai.',
     '{ptName}, {date}', utc_timestamp())
on duplicate key update code = code;
