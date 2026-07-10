-- =====================================================================
-- V20 (UC-052/053): Đơn thanh toán VietQR và giao dịch đối soát Casso.
-- payment_orders: 1-1 với booking, ref_code duy nhất đặt trong nội dung
-- chuyển khoản. payment_transactions: sổ giao dịch ngân hàng do Casso gửi
-- (lưu cả giao dịch không khớp để đối soát); external_id UNIQUE bảo đảm
-- idempotency khi webhook bắn trùng.
-- =====================================================================

create table payment_orders (
    amount decimal(14,2) not null,
    booking_id bigint not null,
    created_at datetime(6) not null,
    expires_at datetime(6),
    id bigint not null auto_increment,
    paid_at datetime(6),
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    ref_code varchar(40) not null,
    status enum ('PENDING','PAID','FAILED','EXPIRED','CANCELLED') not null,
    qr_content varchar(500),
    primary key (id)
) engine=InnoDB;

alter table payment_orders
    add constraint UK_payment_orders_booking unique (booking_id);
create unique index idx_payment_ref on payment_orders (ref_code);
alter table payment_orders
    add constraint FK_payment_orders_booking foreign key (booking_id) references bookings (id);

create table payment_transactions (
    amount decimal(14,2) not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    payment_order_id bigint,
    updated_at datetime(6),
    external_id varchar(100) not null,
    ref_code varchar(100),
    created_by varchar(255),
    updated_by varchar(255),
    raw_description varchar(500),
    primary key (id)
) engine=InnoDB;

create unique index idx_paytxn_external on payment_transactions (external_id);
alter table payment_transactions
    add constraint FK_paytxn_order foreign key (payment_order_id) references payment_orders (id);
