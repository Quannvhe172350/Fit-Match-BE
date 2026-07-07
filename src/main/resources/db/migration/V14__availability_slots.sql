-- =====================================================================
-- V14 (UC-028): Lịch rảnh lặp hàng tuần của PT.
-- =====================================================================

create table availability_slots (
    day_of_week integer not null,
    end_time time(6) not null,
    start_time time(6) not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_availability_pt_day on availability_slots (pt_profile_id, day_of_week);

alter table availability_slots
    add constraint FK_availability_slots_pt
    foreign key (pt_profile_id) references pt_profiles (id);
