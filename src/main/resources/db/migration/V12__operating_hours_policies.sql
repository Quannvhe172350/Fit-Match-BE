-- =====================================================================
-- V12 (UC-017): Giờ hoạt động, sức chứa chi nhánh và chính sách vận hành Gym.
-- =====================================================================

alter table gym_branches
    add column capacity integer null;

create table operating_hours (
    closed bit not null,
    close_time time(6),
    day_of_week integer not null,
    open_time time(6),
    created_at datetime(6) not null,
    gym_branch_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

alter table operating_hours
    add constraint uk_operating_hours_branch_day unique (gym_branch_id, day_of_week);

alter table operating_hours
    add constraint FK_operating_hours_branch
    foreign key (gym_branch_id) references gym_branches (id);

create table gym_policies (
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    booking_policy varchar(2000),
    cancellation_policy varchar(2000),
    house_rules varchar(2000),
    no_show_policy varchar(2000),
    primary key (id)
) engine=InnoDB;

alter table gym_policies
    add constraint UK_gym_policies_gym unique (gym_profile_id);

alter table gym_policies
    add constraint FK_gym_policies_gym
    foreign key (gym_profile_id) references gym_profiles (id);
