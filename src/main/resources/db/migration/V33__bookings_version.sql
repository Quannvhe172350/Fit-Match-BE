-- =====================================================================
-- V33 (UC-038/042/059): optimistic lock cho bookings.
-- Chống lost-update giữa các luồng đồng thời trên cùng booking: gym accept
-- vs customer cancel, webhook thanh toán vs cancel (tránh tiền kẹt), double
-- checkout booking 0đ, và double settlement release khi chạy nhiều instance.
-- Cột version do JPA @Version quản lý.
-- =====================================================================
ALTER TABLE bookings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
