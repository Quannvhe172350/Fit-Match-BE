-- =====================================================================
-- V89: các tham số cấu hình mà mô hình "Gym xếp ca — PT xin nghỉ" cần.
--
-- 1) Quyết định §4.2 — hạn mức đơn nghỉ mỗi tháng, CẤP GYM và BẬT-TẮT được.
--    Để trên gym_profiles chứ không phải gym_policies: gym_policies là bốn
--    trường văn bản với PUT upsert TOÀN PHẦN (UC-017), nhét số vào đó thì mỗi
--    lần Gym sửa nội quy sẽ xoá mất hạn mức.
--
-- 2) Quyết định §4.1 — số giờ tối thiểu PT phải báo trước, do ADMIN chỉnh.
--    Dùng system_configs theo đúng quy tắc UC-078: key seed bằng migration,
--    có code đọc thật (PtLeaveRequestServiceImpl), API chỉ update giá trị.
--
-- 3) tickets.pt_refunded_amount — bắt buộc để hoàn lẻ phụ phí PT không phá
--    bất biến tiền của PartialRefundCalculator. Không có cột này thì: hoàn lẻ
--    một buổi rồi sau đó hoàn cả vé sẽ hoàn vượt số khách đã trả, và
--    WalletServiceImpl không bắt được vì nó kiểm held_balance TỔNG của Gym
--    chứ không theo từng vé.
-- =====================================================================

alter table gym_profiles
    add column leave_quota_enabled boolean not null default false,
    add column leave_monthly_quota int null;

alter table tickets
    add column pt_refunded_amount decimal(12,2) not null default 0;

insert into system_configs (config_key, config_value, description, created_at)
values ('pt.leave.min-lead-hours', '48',
        'Số giờ tối thiểu PT phải nộp đơn nghỉ trước giờ bắt đầu buổi tập đã có khách đặt. Đơn không đè lên buổi nào thì không áp ràng buộc này.',
        utc_timestamp())
on duplicate key update config_key = config_key;

insert into notification_templates (code, title, body, placeholders, created_at) values
    ('PT_LEAVE_SUBMITTED', 'PT gửi đơn xin nghỉ',
     '{ptName} xin nghỉ từ {fromDate} đến {toDate} ({type}): {reason}. Vào duyệt đơn để chốt lịch.',
     '{ptName}, {fromDate}, {toDate}, {type}, {reason}', utc_timestamp()),
    ('PT_LEAVE_APPROVED', 'Đơn nghỉ đã được duyệt',
     'Đơn nghỉ {fromDate} - {toDate} đã được duyệt. Số buổi tập bị ảnh hưởng: {affected}.',
     '{fromDate}, {toDate}, {affected}', utc_timestamp()),
    ('PT_LEAVE_REJECTED', 'Đơn nghỉ bị từ chối',
     'Đơn nghỉ {fromDate} - {toDate} bị từ chối: {reason}. Lịch ca của bạn giữ nguyên.',
     '{fromDate}, {toDate}, {reason}', utc_timestamp()),
    ('SESSION_PT_CANCELLED', 'HLV của buổi tập xin nghỉ',
     'HLV {ptName} không thể phụ trách buổi ngày {date} lúc {slotStart}. Bạn có thể chọn HLV khác hoặc nhận hoàn {refundAmount} đ phụ phí HLV của ngày này.',
     '{ptName}, {date}, {slotStart}, {refundAmount}', utc_timestamp()),
    ('SESSION_PT_AUTO_REFUNDED', 'Đã hoàn phụ phí HLV của buổi tập',
     'Buổi ngày {date} không được chọn HLV thay thế nên {refundAmount} đ phụ phí HLV đã được hoàn vào ví của bạn.',
     '{date}, {refundAmount}', utc_timestamp()),
    ('PT_SHIFT_NOT_ROSTERED', 'PT chưa được xếp ca',
     'PT {ptName} đang hoạt động nhưng chưa có ca nào trong {days} ngày tới — khách không đặt được HLV này.',
     '{ptName}, {days}', utc_timestamp())
on duplicate key update code = code;
