-- =====================================================================
-- V5 (UC-019): PT thuộc quyền quản lý của Gym.
-- - pt_profiles.gym_profile_id: Gym chịu trách nhiệm (nullable cho dữ liệu
--   PT self-registered cũ; hồ sơ mới luôn có Gym).
-- - pt_profiles.status: trạng thái hoạt động do Gym/Admin điều khiển
--   (ACTIVE/INACTIVE/SUSPENDED) — thay cho verification_status cũ.
-- - PT cũ (không thuộc Gym) đặt INACTIVE theo quyết định nghiệp vụ:
--   mô hình self-verification đã bỏ, Gym sẽ tạo lại PT dưới quyền mình.
-- =====================================================================

alter table pt_profiles
    add column gym_profile_id bigint null;

alter table pt_profiles
    add constraint FK_pt_profiles_gym_profile
    foreign key (gym_profile_id) references gym_profiles (id);

create index idx_pt_profiles_gym on pt_profiles (gym_profile_id);

alter table pt_profiles
    add column status enum ('ACTIVE','INACTIVE','SUSPENDED') not null default 'INACTIVE';

alter table pt_profiles
    add column suspension_reason varchar(1000) null;
