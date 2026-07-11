-- =====================================================================
-- V21 (UC-055/056/062): Yêu cầu hoàn tiền và yêu cầu rút tiền (payout).
-- refund_requests: gắn với booking, xử lý hoàn tiền/điều chỉnh.
-- withdrawal_requests: gắn với wallet của gym, xử lý rút tiền.
-- =====================================================================

create table refund_requests (
    amount decimal(14,2) not null,
    booking_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    reason varchar(500),
    decision_note varchar(500),
    status enum ('PENDING','APPROVED','REJECTED','EXECUTED') not null,
    primary key (id)
) engine=InnoDB;

create index idx_refund_booking on refund_requests (booking_id);
alter table refund_requests
    add constraint FK_refund_booking foreign key (booking_id) references bookings (id);

create table withdrawal_requests (
    amount decimal(14,2) not null,
    wallet_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    bank_account varchar(50) not null,
    bank_name varchar(100) not null,
    account_holder varchar(150) not null,
    review_note varchar(500),
    status enum ('PENDING','APPROVED','REJECTED','PAID') not null,
    primary key (id)
) engine=InnoDB;

create index idx_withdrawal_wallet on withdrawal_requests (wallet_id, status);
alter table withdrawal_requests
    add constraint FK_withdrawal_wallet foreign key (wallet_id) references wallets (id);
