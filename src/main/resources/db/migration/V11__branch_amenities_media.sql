-- =====================================================================
-- V11 (UC-016): Facility gắn chi nhánh, tiện ích chi nhánh, ảnh/media Gym.
-- =====================================================================

alter table gym_facilities
    add column gym_branch_id bigint null;

alter table gym_facilities
    add constraint FK_gym_facilities_branch
    foreign key (gym_branch_id) references gym_branches (id);

alter table gym_branches
    add column amenities varchar(1000) null;

create table gym_media (
    created_at datetime(6) not null,
    gym_branch_id bigint,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    caption varchar(255),
    created_by varchar(255),
    updated_by varchar(255),
    url varchar(500) not null,
    primary key (id)
) engine=InnoDB;

alter table gym_media
    add constraint FK_gym_media_gym
    foreign key (gym_profile_id) references gym_profiles (id);

alter table gym_media
    add constraint FK_gym_media_branch
    foreign key (gym_branch_id) references gym_branches (id);
