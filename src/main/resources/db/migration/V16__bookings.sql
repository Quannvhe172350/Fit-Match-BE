-- =====================================================================
-- V16 (UC-031/UC-040): Booking và lịch sử trạng thái.
-- =====================================================================

create table bookings (
    late_cancellation bit not null,
    payable_amount decimal(12,2),
    total_amount decimal(12,2),
    created_at datetime(6) not null,
    customer_id bigint not null,
    end_at datetime(6),
    gym_branch_id bigint,
    gym_profile_id bigint not null,
    gym_service_id bigint,
    id bigint not null auto_increment,
    pt_profile_id bigint,
    start_at datetime(6),
    training_package_id bigint,
    updated_at datetime(6),
    status enum ('CANCELLED','COMPLETED','CONFIRMED','DRAFT','NO_SHOW','PENDING_GYM','PENDING_PAYMENT','REJECTED') not null,
    created_by varchar(255),
    updated_by varchar(255),
    customer_note varchar(500),
    status_reason varchar(500),
    primary key (id)
) engine=InnoDB;

create index idx_bookings_customer on bookings (customer_id, status);
create index idx_bookings_gym_status on bookings (gym_profile_id, status);
create index idx_bookings_pt_time on bookings (pt_profile_id, start_at, end_at);

alter table bookings
    add constraint FK_bookings_customer foreign key (customer_id) references users (id);
alter table bookings
    add constraint FK_bookings_gym foreign key (gym_profile_id) references gym_profiles (id);
alter table bookings
    add constraint FK_bookings_branch foreign key (gym_branch_id) references gym_branches (id);
alter table bookings
    add constraint FK_bookings_service foreign key (gym_service_id) references gym_services (id);
alter table bookings
    add constraint FK_bookings_package foreign key (training_package_id) references training_packages (id);
alter table bookings
    add constraint FK_bookings_pt foreign key (pt_profile_id) references pt_profiles (id);

create table booking_status_history (
    booking_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    from_status enum ('CANCELLED','COMPLETED','CONFIRMED','DRAFT','NO_SHOW','PENDING_GYM','PENDING_PAYMENT','REJECTED'),
    to_status enum ('CANCELLED','COMPLETED','CONFIRMED','DRAFT','NO_SHOW','PENDING_GYM','PENDING_PAYMENT','REJECTED') not null,
    created_by varchar(255),
    updated_by varchar(255),
    reason varchar(500),
    primary key (id)
) engine=InnoDB;

create index idx_bsh_booking on booking_status_history (booking_id);

alter table booking_status_history
    add constraint FK_bsh_booking foreign key (booking_id) references bookings (id);
