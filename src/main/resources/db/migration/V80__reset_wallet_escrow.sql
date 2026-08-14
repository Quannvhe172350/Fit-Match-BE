-- =====================================================================
-- V80 (P4 — ⚠️ GHI ĐÈ SỐ DƯ, rủi ro R3): reset ba bucket escrow.
--
-- held / pending / frozen đều là tiền đang bị GIỮ theo một booking cụ thể.
-- V79 đã xoá hết booking nên ba số này không còn gì để giữ — để nguyên là
-- khoá vĩnh viễn một khoản tiền không ai đòi được.
--
-- available_balance GIỮ NGUYÊN: đó là tiền đã thuộc về chủ ví, rút được, và
-- không liên quan gì tới booking nào.
--
-- Mỗi ví được ghi MỘT bút toán ADJUSTMENT làm số dư đầu kỳ — sổ cái ví vừa bị
-- xoá ở V79 nên nếu không có dòng này thì số dư khả dụng hiện tại không có
-- bản ghi nào giải thích. Đây là dòng đầu tiên của sổ cái mới.
-- =====================================================================

alter table wallet_transactions
    modify column type enum ('HOLD','REFUND','MOVE_TO_PENDING','RELEASE','COMMISSION',
        'FREEZE','UNFREEZE','WITHDRAWAL','DISPUTE_HOLD','REFUND_CREDIT','ADJUSTMENT') not null;

insert into wallet_transactions
    (wallet_id, type, amount, held_after, pending_after, available_after, frozen_after,
     description, created_at, created_by)
select w.id, 'ADJUSTMENT', 0,
       0, 0, w.available_balance, 0,
       concat('Số dư đầu kỳ mô hình vé (V80). Trước reset: held=', w.held_balance,
              ', pending=', w.pending_balance, ', frozen=', w.frozen_balance),
       utc_timestamp(), 'system'
from wallets w;

update wallets
set held_balance = 0,
    pending_balance = 0,
    frozen_balance = 0;
