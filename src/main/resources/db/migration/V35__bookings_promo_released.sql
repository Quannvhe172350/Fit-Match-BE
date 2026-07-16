-- =====================================================================
-- V35 (UC-073, P1 batch 2): cờ đánh dấu đã hoàn điểm/voucher cho booking.
-- Điểm thưởng bị REDEEM và lượt voucher bị tăng usedCount ngay tại checkout,
-- TRƯỚC khi tiền về. Trước fix này, booking bị hủy/từ chối/hết hạn thanh toán
-- không hoàn lại điểm/voucher -> khách mất giá trị thật dù chưa dùng dịch vụ.
-- Cờ này bảo đảm mỗi booking chỉ được hoàn đúng một lần (idempotent) dù đi qua
-- nhiều đường hủy (customer cancel, gym reject, gym cancel, payment expiry).
-- =====================================================================
ALTER TABLE bookings
    ADD COLUMN promo_released bit NOT NULL DEFAULT 0;
