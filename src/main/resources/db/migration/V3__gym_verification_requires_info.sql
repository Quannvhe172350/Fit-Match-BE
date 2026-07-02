-- =====================================================================
-- V3 (UC-013): Admin có thể trả hồ sơ Gym về trạng thái "cần bổ sung"
-- (REQUIRES_INFO) kèm ghi chú review.
-- VerificationStatus là enum dùng chung cho cả Gym và PT nên cả hai cột
-- đều phải mở rộng danh sách giá trị.
-- =====================================================================

alter table gym_profiles
    modify column verification_status enum (
        'APPROVED',
        'NOT_SUBMITTED',
        'PENDING',
        'REJECTED',
        'REQUIRES_INFO'
    ) not null;

alter table pt_profiles
    modify column verification_status enum (
        'APPROVED',
        'NOT_SUBMITTED',
        'PENDING',
        'REJECTED',
        'REQUIRES_INFO'
    ) not null;

alter table gym_profiles
    add column review_note varchar(1000) null;
