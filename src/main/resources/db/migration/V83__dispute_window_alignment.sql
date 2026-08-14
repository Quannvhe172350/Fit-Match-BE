-- =====================================================================
-- V83 (D-18): đồng bộ "hạn mở tranh chấp" với "hạn giữ tiền gym".
--
-- Trước migration này hai con số đo hai đằng: cửa sổ tranh chấp seed 14 ngày
-- (V49) nhưng KHÔNG có code nào đọc, còn tiền thì tự về gym sau 3 ngày
-- (settlement_hold_days, V18). Kết quả: từ ngày thứ 4 khách vẫn mở được tranh
-- chấp nhưng không còn đồng nào để đóng băng, và quyết định hoàn tiền của
-- moderator chết ở DisputeFinancialApplier.
--
-- Chốt: cả hai = 7 ngày, cùng neo vào tickets.settlement_pending_at.
-- Đổi về sau thì giữ nguyên bất biến: dispute.open-window-days <= settlement_hold_days.
-- =====================================================================

update system_configs
set config_value = '7',
    description  = 'Số ngày được mở tranh chấp kể từ khi vé được chốt hoàn thành/hết hạn (UC-063). Phải <= settlement_hold_days',
    updated_at   = utc_timestamp(),
    updated_by   = 'system'
where config_key = 'dispute.open-window-days';

-- commission_configs là bảng LỊCH SỬ (currentConfig = row id lớn nhất), nên
-- thêm bản mới chứ không sửa bản cũ — giữ nguyên % hoa hồng/phí đang áp dụng.
insert into commission_configs
    (commission_percent, platform_fee_percent, settlement_hold_days, created_at, created_by)
select commission_percent, platform_fee_percent, 7, utc_timestamp(), 'system'
from commission_configs
order by id desc
limit 1;
