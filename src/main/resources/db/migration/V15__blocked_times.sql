-- =====================================================================
-- V15 (UC-029): Khoảng thời gian không nhận đặt lịch (PT hoặc chi nhánh).
-- =====================================================================

create table blocked_times (
    created_at datetime(6) not null,
    end_at datetime(6) not null,
    gym_branch_id bigint,
    id bigint not null auto_increment,
    pt_profile_id bigint,
    start_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    reason varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_blocked_pt_time on blocked_times (pt_profile_id, start_at, end_at);
create index idx_blocked_branch_time on blocked_times (gym_branch_id, start_at, end_at);

alter table blocked_times
    add constraint FK_blocked_times_pt
    foreign key (pt_profile_id) references pt_profiles (id);

alter table blocked_times
    add constraint FK_blocked_times_branch
    foreign key (gym_branch_id) references gym_branches (id);
