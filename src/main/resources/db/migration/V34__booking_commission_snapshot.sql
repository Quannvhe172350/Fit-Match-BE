-- =====================================================================
-- V34 (UC-072/P1-7): chốt % hoa hồng vào booking khi chuyển pending settlement.
-- Tránh áp hồi tố khi Admin đổi commission config sau khi buổi tập hoàn tất.
-- Booking cũ (cột null) sẽ fallback về config hiện hành lúc release.
-- =====================================================================
ALTER TABLE bookings
    ADD COLUMN commission_percent DECIMAL(5,2);
