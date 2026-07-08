-- =====================================================================
-- V17 (UC-044): Danh sách chờ khi slot mong muốn không còn.
-- =====================================================================

create table waitlist_entries (
    active bit not null,
    created_at datetime(6) not null,
    customer_id bigint not null,
    gym_service_id bigint,
    id bigint not null auto_increment,
    preferred_start datetime(6),
    training_package_id bigint,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    note varchar(500),
    primary key (id)
) engine=InnoDB;

create index idx_waitlist_service on waitlist_entries (gym_service_id, active);
create index idx_waitlist_package on waitlist_entries (training_package_id, active);

alter table waitlist_entries
    add constraint FK_waitlist_customer foreign key (customer_id) references users (id);
alter table waitlist_entries
    add constraint FK_waitlist_service foreign key (gym_service_id) references gym_services (id);
alter table waitlist_entries
    add constraint FK_waitlist_package foreign key (training_package_id) references training_packages (id);
