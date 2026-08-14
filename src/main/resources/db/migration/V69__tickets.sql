-- =====================================================================
-- V69 (mô hình vé — quyết định #3): Ticket là đơn vị escrow. Một lần mua =
-- một vé; mọi dòng tiền (payment_orders, refund_requests, disputes,
-- wallet_transactions, settlement) đều neo vào vé chứ không vào buổi tập.
--
-- Toàn bộ giá được SNAPSHOT tại thời điểm mua (unit_price, day_count,
-- pt_surcharge_per_day): gym đổi bảng giá về sau không làm sai vé đã bán.
-- expires_at cũng chốt lúc mua từ platform_ticket_config (câu 32).
--
-- start_date  : ngày đầu tiên của vé, ghi khi khách đặt lịch (null = chưa đặt).
--               Vé gói dùng làm mốc tính hoàn một phần theo số ngày đã qua.
-- with_pt     : khách đã trả phụ phí PT cho vé này hay chưa. Phụ phí tính theo
--               VÉ (mọi ngày), nên bỏ PT khỏi một ngày không hoàn tiền.
-- promo_released: cờ idempotent hoàn điểm thưởng / trả lượt voucher khi vé bị
--               huỷ hoặc hoàn — giữ nguyên cơ chế của bookings.promo_released.
-- =====================================================================

create table tickets (
    id bigint not null auto_increment,
    customer_id bigint not null,
    ticket_type_id bigint not null,
    gym_profile_id bigint not null,
    gym_branch_id bigint not null,
    kind varchar(20) not null,
    day_count int not null,
    with_pt bit not null default 0,
    unit_price decimal(12,2) not null,
    pt_surcharge_per_day decimal(12,2),
    total_amount decimal(12,2) not null,
    voucher_id bigint,
    discount_amount decimal(12,2),
    loyalty_points_used int,
    payable_amount decimal(12,2) not null,
    status varchar(20) not null,
    status_reason varchar(500),
    purchased_at datetime(6),
    expires_at datetime(6),
    start_date date,
    promo_released bit not null default 0,
    settlement_status varchar(20) not null default 'NONE',
    settlement_amount decimal(14,2),
    settlement_pending_at datetime(6),
    commission_percent decimal(5,2),
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_tickets_customer on tickets (customer_id, status);
create index idx_tickets_branch_status on tickets (gym_branch_id, status);
-- TicketExpiryJob quét đúng hai cột này; không có index là full scan mỗi lần chạy.
create index idx_tickets_expiry on tickets (status, expires_at);
create index idx_tickets_settlement on tickets (settlement_status, settlement_pending_at);

alter table tickets
    add constraint FK_tickets_customer foreign key (customer_id) references users (id);
alter table tickets
    add constraint FK_tickets_type foreign key (ticket_type_id) references ticket_types (id);
alter table tickets
    add constraint FK_tickets_gym foreign key (gym_profile_id) references gym_profiles (id);
alter table tickets
    add constraint FK_tickets_branch foreign key (gym_branch_id) references gym_branches (id);
alter table tickets
    add constraint FK_tickets_voucher foreign key (voucher_id) references vouchers (id);
