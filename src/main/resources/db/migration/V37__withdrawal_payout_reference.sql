-- D-11 (audit 2026-07-17): lệnh chi cần mã giao dịch chuyển khoản để đối soát
-- với sao kê ngân hàng — trước đây mark-paid chỉ ghi reviewNote free-text.
ALTER TABLE withdrawal_requests
    ADD COLUMN payout_reference VARCHAR(100) NULL AFTER review_note;
