-- =====================================================================
-- V24 (UC-046..051): check-in, ghi chú buổi tập và gói tập đã mua.
-- =====================================================================

-- Gói tập khách đã mua: kích hoạt khi booking mua gói hoàn tất buổi đầu (UC-049).
create table customer_packages (
    id bigint not null auto_increment,
    customer_id bigint not null,
    training_package_id bigint not null,
    purchase_booking_id bigint not null,
    sessions_total int not null,
    sessions_used int not null default 0,
    expires_at datetime(6) null,
    status varchar(20) not null default 'ACTIVE',
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_customer_packages_purchase (purchase_booking_id),
    key idx_customer_packages_owner (customer_id, status),
    constraint fk_cp_customer foreign key (customer_id) references users (id),
    constraint fk_cp_package foreign key (training_package_id) references training_packages (id),
    constraint fk_cp_booking foreign key (purchase_booking_id) references bookings (id)
) engine = InnoDB;

-- Ghi chú/bằng chứng buổi tập (UC-048).
create table session_notes (
    id bigint not null auto_increment,
    booking_id bigint not null,
    note varchar(2000) not null,
    evidence_url varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_session_notes_booking (booking_id),
    constraint fk_sn_booking foreign key (booking_id) references bookings (id)
) engine = InnoDB;

-- Check-in (UC-046) + liên kết buổi tập với gói đã mua (UC-049).
alter table bookings
    add column checked_in_at datetime(6) null,
    add column customer_package_id bigint null,
    add constraint fk_bookings_customer_package
        foreign key (customer_package_id) references customer_packages (id);
