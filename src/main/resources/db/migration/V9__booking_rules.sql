-- =====================================================================
-- V9 (UC-026): Quy tắc thanh toán/đặt lịch trên dịch vụ và gói tập:
-- % đặt cọc, hạn hủy miễn phí (giờ), thời gian đặt trước tối thiểu (giờ).
-- Null = dùng mặc định nền tảng. Hoa hồng cấu hình ở cấp platform (UC-072).
-- =====================================================================

alter table gym_services
    add column deposit_percent integer null,
    add column free_cancellation_hours integer null,
    add column min_notice_hours integer null;

alter table training_packages
    add column deposit_percent integer null,
    add column free_cancellation_hours integer null,
    add column min_notice_hours integer null;
