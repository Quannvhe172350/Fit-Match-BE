-- =====================================================================
-- V57 (bug S2-05): giá khi CÓ PT và KHÔNG có PT phải khác nhau, mức chênh do
-- từng phòng gym quyết định.
--
-- Quy ước: cột `price` giữ nguyên nghĩa cũ = giá KHÔNG kèm PT (khách tự tập);
-- `pt_surcharge` là phần cộng thêm khi khách chọn PT. NULL = gym không tính
-- thêm, nên dữ liệu cũ giữ nguyên hành vi hiện tại (không đội giá ngược).
-- BookingPriceCalculator cộng phụ phí này vào totalAmount lúc checkout.
-- =====================================================================

alter table gym_services
    add column pt_surcharge decimal(12, 2) null after price;

alter table training_packages
    add column pt_surcharge decimal(12, 2) null after price;
