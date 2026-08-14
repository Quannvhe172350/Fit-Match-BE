-- =====================================================================
-- V79 (P4 — ⚠️ XOÁ DỮ LIỆU, câu 21): dọn sạch dữ liệu giao dịch để nền tảng
-- khởi động lại trên mô hình vé.
--
-- KHÔNG đụng tới: users, gym_profiles, gym_branches, pt_profiles, vouchers,
-- loyalty_accounts + loyalty_transactions, wallets.available_balance.
--
-- Vì sao GIỮ sổ cái điểm thưởng nhưng XOÁ sổ cái ví: điểm thưởng là tài sản
-- của khách và số dư điểm được giữ nguyên, nên xoá lịch sử sẽ làm số dư không
-- còn giải thích được. Ngược lại, mọi dòng wallet_transactions đều neo vào
-- booking/vé sắp bị xoá — giữ lại là giữ những dòng trỏ vào hư không. V80 ghi
-- một bút toán số dư đầu kỳ để sổ cái ví vẫn tự giải thích được.
--
-- Xoá theo thứ tự khoá ngoại: con trước, cha sau.
-- =====================================================================

-- Con của reviews / disputes / payment_orders
delete from review_reports;
delete from dispute_evidence;
delete from payment_transactions;

-- Lịch sử trạng thái
delete from session_status_history;
delete from ticket_status_history;
delete from booking_status_history;
delete from session_notes;

-- Bảng giao dịch neo vào booking/vé
delete from reviews;
delete from disputes;
delete from refund_requests;
delete from payment_orders;
delete from waitlist_entries;
delete from customer_packages;

-- Đơn vị nghiệp vụ
delete from training_sessions;
delete from tickets;
delete from bookings;

-- Sổ cái ví: mọi dòng đều trỏ vào booking/vé vừa xoá.
delete from wallet_transactions;
