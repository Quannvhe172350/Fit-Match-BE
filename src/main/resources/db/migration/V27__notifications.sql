-- =====================================================================
-- V27 (UC-075): thông báo in-app theo sự kiện.
-- =====================================================================

create table notifications (
    id bigint not null auto_increment,
    user_id bigint not null,
    category varchar(20) not null,
    title varchar(200) not null,
    body varchar(1000) null,
    link varchar(300) null,
    read_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_notifications_user (user_id, read_at),
    constraint fk_notifications_user foreign key (user_id) references users (id)
) engine = InnoDB;
