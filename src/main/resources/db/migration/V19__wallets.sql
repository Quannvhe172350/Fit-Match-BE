-- =====================================================================
-- V19 (UC-057/061): Ví Gym và sổ cái ví (append-only).
-- Tiền booking do nền tảng giữ (RP1); đi qua held -> pending -> available.
-- =====================================================================

create table wallets (
    available_balance decimal(14,2) not null,
    frozen_balance decimal(14,2) not null,
    held_balance decimal(14,2) not null,
    pending_balance decimal(14,2) not null,
    version bigint not null,
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

alter table wallets
    add constraint UK_wallets_gym unique (gym_profile_id);
alter table wallets
    add constraint FK_wallets_gym foreign key (gym_profile_id) references gym_profiles (id);

create table wallet_transactions (
    amount decimal(14,2) not null,
    available_after decimal(14,2) not null,
    booking_id bigint,
    frozen_after decimal(14,2) not null,
    held_after decimal(14,2) not null,
    pending_after decimal(14,2) not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    wallet_id bigint not null,
    created_by varchar(255),
    updated_by varchar(255),
    description varchar(500),
    type enum ('HOLD','REFUND','MOVE_TO_PENDING','RELEASE','COMMISSION','FREEZE','UNFREEZE','WITHDRAWAL') not null,
    primary key (id)
) engine=InnoDB;

create index idx_wallet_txn_wallet on wallet_transactions (wallet_id);

alter table wallet_transactions
    add constraint FK_wallet_txn_wallet foreign key (wallet_id) references wallets (id);
