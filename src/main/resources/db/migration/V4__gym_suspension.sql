-- =====================================================================
-- V4 (UC-014): Đình chỉ / kích hoạt lại Gym vi phạm chính sách.
-- Thêm SUSPENDED vào enum verification_status (dùng chung Gym + PT).
-- =====================================================================

alter table gym_profiles
    modify column verification_status enum (
        'APPROVED',
        'NOT_SUBMITTED',
        'PENDING',
        'REJECTED',
        'REQUIRES_INFO',
        'SUSPENDED'
    ) not null;

alter table pt_profiles
    modify column verification_status enum (
        'APPROVED',
        'NOT_SUBMITTED',
        'PENDING',
        'REJECTED',
        'REQUIRES_INFO',
        'SUSPENDED'
    ) not null;
