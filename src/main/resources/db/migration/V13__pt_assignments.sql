-- =====================================================================
-- V13 (UC-022): Gán PT vào chi nhánh / dịch vụ / gói tập.
-- Mỗi dòng một liên kết (đúng một đích khác null — service layer đảm bảo).
-- =====================================================================

create table pt_assignments (
    active bit not null,
    created_at datetime(6) not null,
    gym_branch_id bigint,
    gym_service_id bigint,
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    training_package_id bigint,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

alter table pt_assignments
    add constraint uk_pt_assignment_branch unique (pt_profile_id, gym_branch_id);

alter table pt_assignments
    add constraint uk_pt_assignment_service unique (pt_profile_id, gym_service_id);

alter table pt_assignments
    add constraint uk_pt_assignment_package unique (pt_profile_id, training_package_id);

alter table pt_assignments
    add constraint FK_pt_assignments_pt
    foreign key (pt_profile_id) references pt_profiles (id);

alter table pt_assignments
    add constraint FK_pt_assignments_branch
    foreign key (gym_branch_id) references gym_branches (id);

alter table pt_assignments
    add constraint FK_pt_assignments_service
    foreign key (gym_service_id) references gym_services (id);

alter table pt_assignments
    add constraint FK_pt_assignments_package
    foreign key (training_package_id) references training_packages (id);
