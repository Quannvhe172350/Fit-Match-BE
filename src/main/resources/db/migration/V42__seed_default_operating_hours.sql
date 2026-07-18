-- =====================================================================
-- V42 (UC-017/UC-030): Backfill giờ hoạt động mặc định 06:00-22:00 (cả tuần)
-- cho các chi nhánh chưa cấu hình ngày nào — trước đây các chi nhánh này
-- chặn mọi booking với lỗi "chưa cấu hình giờ hoạt động".
-- Chi nhánh mới tạo từ nay được seed mặc định trong GymBranchServiceImpl.
-- =====================================================================

insert into operating_hours (gym_branch_id, day_of_week, open_time, close_time, closed, created_at, created_by)
select b.id, d.day_of_week, '06:00:00', '22:00:00', 0, now(6), 'system:V42'
from gym_branches b
cross join (
    select 1 as day_of_week union all select 2 union all select 3 union all
    select 4 union all select 5 union all select 6 union all select 7
) d
where not exists (
    select 1 from operating_hours oh where oh.gym_branch_id = b.id
);
