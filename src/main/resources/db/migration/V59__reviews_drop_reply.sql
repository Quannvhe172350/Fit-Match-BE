-- =====================================================================
-- V59 (UC-069): review chỉ để HIỂN THỊ — bỏ hoàn toàn tính năng gym phản
-- hồi đánh giá. Gỡ 3 cột reply và template thông báo tương ứng; điều kiện
-- đánh giá (booking COMPLETED của chính khách) giữ nguyên ở tầng service.
-- =====================================================================

alter table reviews
    drop column reply,
    drop column replied_by,
    drop column replied_at;

delete from notification_templates where code = 'REVIEW_REPLIED_CUSTOMER';
