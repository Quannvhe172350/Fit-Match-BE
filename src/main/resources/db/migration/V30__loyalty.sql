-- =====================================================================
-- V30 (UC-073): điểm thưởng (loyalty) — tài khoản, sổ cái và điểm dùng trên booking.
-- =====================================================================

create table loyalty_accounts (
    id bigint not null auto_increment,
    user_id bigint not null,
    points_balance int not null default 0,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_loyalty_accounts_user (user_id),
    constraint fk_loyalty_accounts_user foreign key (user_id) references users (id)
) engine = InnoDB;

create table loyalty_transactions (
    id bigint not null auto_increment,
    account_id bigint not null,
    type varchar(10) not null,
    points int not null,
    booking_id bigint null,
    balance_after int not null,
    description varchar(255) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_loyalty_txn_account (account_id),
    constraint fk_loyalty_txn_account foreign key (account_id) references loyalty_accounts (id)
) engine = InnoDB;

alter table bookings
    add column loyalty_points_used int null;
