-- =====================================================================
-- V8 (UC-025): Gói tập của Gym (số buổi, hạn dùng, giá, điều kiện sử dụng).
-- =====================================================================

create table training_packages (
    active bit not null,
    price decimal(12,2) not null,
    session_count integer not null,
    validity_days integer,
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    gym_service_id bigint,
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(150) not null,
    created_by varchar(255),
    updated_by varchar(255),
    description varchar(1000),
    usage_conditions varchar(1000),
    primary key (id)
) engine=InnoDB;

alter table training_packages
    add constraint FK_training_packages_gym
    foreign key (gym_profile_id) references gym_profiles (id);

alter table training_packages
    add constraint FK_training_packages_service
    foreign key (gym_service_id) references gym_services (id);

create index idx_training_packages_gym on training_packages (gym_profile_id);
