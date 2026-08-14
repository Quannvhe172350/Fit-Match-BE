-- =====================================================================
-- V84 (bug S2-09, nối tiếp V83): nối lại cửa sổ gửi yêu cầu hoàn tiền.
--
-- Key 'refund.request-window-days' seed ở V56 cho mô hình booking cũ ("kể từ khi
-- buổi tập kết thúc") và cũng chưa từng có code nào đọc. Mô hình vé không còn
-- mốc đó: yêu cầu hoàn chỉ mở khi vé đang ACTIVE, tức là TRƯỚC khi dịch vụ kết
-- thúc. Mốc tương đương duy nhất còn nghĩa là ngày tập đầu tiên của vé —
-- cùng mốc PartialRefundCalculator đếm số ngày đã dùng.
--
-- Giữ nguyên giá trị 7, chỉ sửa mô tả cho khớp cái mà RefundRequestWindow làm.
-- =====================================================================

update system_configs
set description = 'Số ngày khách được gửi yêu cầu hoàn tiền, tính từ ngày tập đầu tiên của vé (UC-055). Vé chưa xếp lịch thì không áp hạn. 0 = không giới hạn (chỉ đặt được qua env)',
    updated_at  = utc_timestamp(),
    updated_by  = 'system'
where config_key = 'refund.request-window-days';
