-- =====================================================================
-- V31 (UC-063/067): bổ sung giá trị DISPUTE_HOLD vào ledger ví.
-- Code (WalletServiceImpl.reverseToHeld) đã ghi loại bút toán này từ trước,
-- nhưng cột enum ở V19 chưa liệt kê -> INSERT fail runtime khi mở tranh chấp
-- trên booking đang chờ giải ngân. Append giá trị vào cuối enum là thao tác
-- metadata (nhanh) trên MariaDB, không ảnh hưởng dữ liệu cũ, không đổi thứ tự
-- lưu (Hibernate lưu enum theo tên chuỗi).
-- =====================================================================
ALTER TABLE wallet_transactions
    MODIFY COLUMN type ENUM(
        'HOLD','REFUND','MOVE_TO_PENDING','RELEASE','COMMISSION',
        'FREEZE','UNFREEZE','WITHDRAWAL','DISPUTE_HOLD'
    ) NOT NULL;
