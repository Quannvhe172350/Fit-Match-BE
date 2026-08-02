-- =====================================================================
-- V56 (bug S2-08/S2-09):
--  1. Cửa sổ thời gian khách được gửi yêu cầu hoàn tiền — đọc bởi
--     RefundServiceImpl (system_configs override env/@Value như V49).
--  2. Template thông báo khi Admin TỪ CHỐI hoàn tiền (UC-075/V52). Trước đây
--     luồng reject chỉ ghi audit log nên khách không hề nhận được thông báo.
-- =====================================================================

insert into system_configs (config_key, config_value, description, created_at)
values ('refund.request-window-days', '7',
        'Số ngày khách được gửi yêu cầu hoàn tiền kể từ khi buổi tập kết thúc hoặc booking bị hủy/từ chối (UC-055). 0 = không giới hạn',
        utc_timestamp())
on duplicate key update config_key = config_key;

insert into notification_templates (code, title, body, placeholders, created_at)
values ('REFUND_REJECTED_CUSTOMER', 'Yêu cầu hoàn tiền bị từ chối',
        'Yêu cầu hoàn {amount} cho booking #{bookingId} đã bị từ chối: {note}. Nếu chưa đồng ý, bạn có thể mở tranh chấp cho booking này.',
        '{bookingId}, {amount}, {note}', utc_timestamp())
on duplicate key update code = code;
