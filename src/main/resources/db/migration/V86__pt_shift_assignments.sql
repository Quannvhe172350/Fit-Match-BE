-- =====================================================================
-- V86: Gym xếp PT vào ca theo NGÀY CỤ THỂ (roster).
--
-- Vì sao materialize theo từng ngày thay vì lưu quy tắc lặp: lưới phân ca và
-- PtSlotValidator đều hỏi "ngày D, PT P có ca nào" — trả lời bằng một dòng
-- index-lookup rẻ hơn nhiều so với giải quy tắc lặp mỗi lần đọc. Cột source
-- giữ lại vết "dòng này sinh từ xếp lặp hay Gym thêm tay" để màn xếp ca hiển
-- thị đúng và để xoá theo lô không đụng vào ngày Gym đã chỉnh riêng.
--
-- KHÔNG denormalize gym_branch_id (khác training_sessions, nơi bản sao chi
-- nhánh là bắt buộc): ở đây join lên gym_shifts chỉ chạm 3-5 dòng mỗi chi
-- nhánh, luôn nằm trong buffer pool.
-- =====================================================================

create table pt_shift_assignments (
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    gym_shift_id bigint not null,
    work_date date not null,
    -- RECURRING = sinh từ "ca tối, T2/T4/T6, 01/09..30/09"; MANUAL = Gym thêm lẻ.
    source varchar(20) not null,
    -- Tắt mềm thay vì xoá: PT chuyển INACTIVE thì ca tương lai bị vô hiệu nhưng
    -- Gym vẫn nhìn thấy để xếp lại có chủ đích khi bật lại PT.
    active boolean not null default true,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_pt_shift_date (pt_profile_id, gym_shift_id, work_date)
) engine = InnoDB;

-- PtSlotValidator: "PT P ngày D có ca nào".
create index idx_pt_shift_pt_date on pt_shift_assignments (pt_profile_id, work_date);
-- Lưới phân ca của chi nhánh: lấy ca của chi nhánh rồi quét theo khoảng ngày.
create index idx_pt_shift_shift_date on pt_shift_assignments (gym_shift_id, work_date);

alter table pt_shift_assignments
    add constraint FK_pt_shift_pt foreign key (pt_profile_id) references pt_profiles (id);
alter table pt_shift_assignments
    add constraint FK_pt_shift_shift foreign key (gym_shift_id) references gym_shifts (id);
