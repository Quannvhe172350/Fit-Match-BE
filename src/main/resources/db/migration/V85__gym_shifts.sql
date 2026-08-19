-- =====================================================================
-- V85 (đảo chủ thể sở hữu lịch PT): GYM khai CA làm việc ở cấp CHI NHÁNH.
--
-- Trước V85 lịch PT do chính PT khai theo ngày (pt_availabilities, V72).
-- Từ đây Gym là chủ lịch: Gym định nghĩa ca -> xếp PT vào ca (V86) -> PT chỉ
-- xin nghỉ (V87). Xem quyết định §4 trong PROMPT-pt-shift-scheduling.md.
--
-- Ca gắn CHI NHÁNH chứ không gắn Gym: operating_hours và pt_assignments đều
-- neo vào gym_branch_id, nên ca dùng chung cho nhiều chi nhánh sẽ không kiểm
-- được ràng buộc "ca phải nằm trong giờ mở cửa" (hai chi nhánh mở khác giờ).
--
-- days_of_week để CSV ISO-8601 ("1,3,5" = T2/T4/T6) thay vì bảng con: giá trị
-- này LUÔN được đọc trọn (sinh roster, kiểm chồng ca, đối chiếu giờ mở cửa),
-- không bao giờ lọc theo một thứ đơn lẻ.
-- =====================================================================

create table gym_shifts (
    id bigint not null auto_increment,
    gym_branch_id bigint not null,
    name varchar(100) not null,
    start_time time not null,
    end_time time not null,
    -- Độ dài một slot khách đặt. Khách chọn giờ bắt đầu, giờ kết thúc do CA
    -- quyết (trước V85 do PT khai) -> khớp pt_slot_start/pt_slot_end sẵn có.
    slot_minutes int not null default 60,
    days_of_week varchar(20) not null,
    active boolean not null default true,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_gym_shift_branch_name (gym_branch_id, name)
) engine = InnoDB;

create index idx_gym_shifts_branch on gym_shifts (gym_branch_id, active);

alter table gym_shifts
    add constraint FK_gym_shifts_branch foreign key (gym_branch_id) references gym_branches (id);
