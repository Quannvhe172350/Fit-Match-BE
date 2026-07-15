-- =====================================================================
-- V26 (UC-063..068): tranh chấp/khiếu nại và bằng chứng.
-- =====================================================================

create table disputes (
    id bigint not null auto_increment,
    booking_id bigint not null,
    opened_by_role varchar(30) null,
    reason varchar(1000) not null,
    status varchar(20) not null default 'OPEN',
    resolution varchar(20) null,
    refund_amount decimal(14,2) null,
    frozen_amount decimal(14,2) null,
    moderator_note varchar(1000) null,
    resolved_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_disputes_booking (booking_id),
    key idx_disputes_status (status),
    constraint fk_disputes_booking foreign key (booking_id) references bookings (id)
) engine = InnoDB;

create table dispute_evidence (
    id bigint not null auto_increment,
    dispute_id bigint not null,
    description varchar(2000) not null,
    file_url varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_dispute_evidence_dispute (dispute_id),
    constraint fk_dispute_evidence_dispute foreign key (dispute_id) references disputes (id)
) engine = InnoDB;
