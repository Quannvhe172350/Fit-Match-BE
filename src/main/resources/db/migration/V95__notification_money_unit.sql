-- =====================================================================
-- V95 (sheet 7 mục 1): gỡ đơn vị tiền thừa trong mẫu thông báo.
--
-- Thông báo nay định dạng tiền ở tầng Java (NotificationFormat.money) và trả về
-- chuỗi ĐÃ CÓ đơn vị: "50.000 ₫". Những mẫu cũ tự viết thêm " đ" ngay sau chỗ
-- điền nên sẽ render thành "50.000 ₫ đ".
--
-- Dùng replace() trên đúng cặp "{placeholder} đ" thay vì ghi đè cả dòng: admin
-- sửa được nội dung mẫu qua /api/admin/notification-templates, và ghi đè nguyên
-- dòng sẽ xoá mất những chỉnh sửa đó. Cách này chỉ đụng đúng hai ký tự thừa.
--
-- Chỉ ba placeholder mang tiền. {percent}, {date}, {hours} không có đơn vị dính
-- kèm nên không cần chạm tới.
-- =====================================================================

update notification_templates
set title = replace(replace(replace(title,
        '{amount} đ', '{amount}'),
        '{refundAmount} đ', '{refundAmount}'),
        '{netAmount} đ', '{netAmount}'),
    body = replace(replace(replace(body,
        '{amount} đ', '{amount}'),
        '{refundAmount} đ', '{refundAmount}'),
        '{netAmount} đ', '{netAmount}')
where title like '%} đ%'
   or body like '%} đ%';
