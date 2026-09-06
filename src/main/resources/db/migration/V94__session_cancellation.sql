-- =====================================================================
-- V94: KHÁCH HUỶ MỘT NGÀY TẬP, hoàn tiền theo mốc báo trước.
--
-- Trước đây khách chỉ có hai đường: dời ngày (chỉ vé DAY) hoặc xin hoàn CẢ vé
-- qua hàng đợi Admin. Không có đường nào để bỏ đúng một ngày — mà đó lại là
-- việc đời thường nhất: bận đột xuất một hôm.
--
-- Ba cột chính sách nằm ở gym_policies chứ không phải hằng số trong mã: mỗi
-- phòng gym có luật huỷ riêng, và cột cancellation_policy hiện có chỉ là VĂN
-- BẢN cho người đọc — không có gì để máy tính tiền theo.
--
--   huỷ sớm hơn cancel_full_refund_hours     -> hoàn 100%
--   huỷ sớm hơn cancel_partial_refund_hours  -> hoàn cancel_partial_refund_percent
--   muộn hơn nữa                             -> hoàn 0%
--
-- Mặc định 24h/100% - 12h/50% - dưới 12h/0%: gym chưa cấu hình gì vẫn có một
-- luật đọc được, thay vì rơi vào "không hoàn đồng nào" một cách âm thầm.
-- =====================================================================

alter table gym_policies
    add column cancel_full_refund_hours int not null default 24,
    add column cancel_partial_refund_hours int not null default 12,
    add column cancel_partial_refund_percent decimal(5, 2) not null default 50.00;

-- =====================================================================
-- Sổ theo dõi phần đã hoàn lẻ vì huỷ ngày, TÁCH khỏi pt_refunded_amount.
--
-- Hai khoản khác hẳn nhau: pt_refunded_amount là phụ phí HLV của một ngày mất
-- PT (khách vẫn vào tập), còn cột này là giá trị CẢ NGÀY của một ngày khách bỏ.
-- Gộp chung thì không lần ra được vé đã hoàn cái gì, mà PartialRefundCalculator
-- lại phải trừ CẢ HAI khi hoàn cả vé về sau — không trừ thì tổng tiền hoàn vượt
-- số khách đã trả, và WalletService không bắt được vì nó chỉ kiểm held_balance
-- TỔNG của gym chứ không theo từng vé.
-- =====================================================================

alter table tickets
    add column session_refunded_amount decimal(12, 2) not null default 0.00;

-- Số tiền đã hoàn cho đúng buổi đó — để lịch sử vé trả lời được "huỷ hôm ấy
-- được lại bao nhiêu" mà không phải dò ngược ví.
alter table training_sessions
    add column cancel_refund_amount decimal(12, 2) null;

-- Văn bản mặc định nằm trong NotificationDispatcher; hai hàng này để admin sửa
-- được qua /api/admin/notification-templates. Placeholders phải khớp map vars.
insert into notification_templates (code, title, body, placeholders, created_at) values
    ('SESSION_CANCELLED_CUSTOMER', 'Đã huỷ buổi tập ngày {date}',
     'Buổi tập ngày {date} đã được huỷ. Số tiền hoàn lại: {refundAmount} đ (tương đương {percent}% giá trị một ngày tập) và đã vào ví của bạn.',
     '{date}, {refundAmount}, {percent}', utc_timestamp()),
    ('SESSION_CANCELLED_GYM', 'Khách huỷ buổi tập ngày {date}',
     '{customerName} đã huỷ buổi tập ngày {date}. Báo trước {hours} giờ nên hoàn {percent}% giá trị ngày tập.',
     '{customerName}, {date}, {hours}, {percent}', utc_timestamp()),
    ('SESSION_CANCELLED_PT', 'Buổi dạy ngày {date} đã bị huỷ',
     'Khách đã huỷ buổi tập ngày {date} lúc {slot}. Khung giờ này của bạn đã được trả lại.',
     '{date}, {slot}', utc_timestamp())
on duplicate key update code = code;
