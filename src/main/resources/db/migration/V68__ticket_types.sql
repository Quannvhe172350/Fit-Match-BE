-- =====================================================================
-- V68 (mô hình vé — quyết định #1, câu 20): catalog vé thay hoàn toàn
-- gym_services + training_packages.
--
-- Vé được KHAI BÁO ở cấp gym (ticket_types.gym_profile_id) rồi TICK CHỌN các
-- chi nhánh áp dụng qua bảng nối ticket_type_branches — gym 5 chi nhánh chỉ
-- tạo một lần thay vì năm lần. Khách luôn mua vé GẮN VỚI MỘT chi nhánh cụ
-- thể; tickets.gym_branch_id chốt chi nhánh đó tại thời điểm mua.
--
-- kind = DAY   : vé một ngày, day_count luôn = 1
-- kind = PACKAGE: vé gói n ngày liên tiếp, day_count = n
-- pt_surcharge_per_day: phụ phí MỖI NGÀY khi khách chọn có PT (câu 6);
--                       giá gói có PT = price + pt_surcharge_per_day * day_count
-- =====================================================================

create table ticket_types (
    id bigint not null auto_increment,
    gym_profile_id bigint not null,
    name varchar(150) not null,
    description varchar(1000),
    kind varchar(20) not null,
    day_count int not null,
    price decimal(12,2) not null,
    pt_surcharge_per_day decimal(12,2),
    status varchar(20) not null default 'PUBLISHED',
    active bit not null default 1,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_ticket_types_gym on ticket_types (gym_profile_id, status);

alter table ticket_types
    add constraint FK_ticket_types_gym foreign key (gym_profile_id) references gym_profiles (id);

-- Bảng nối: một loại vé áp dụng cho nhiều chi nhánh của chính gym đó.
-- Marketplace liệt kê vé của một chi nhánh bằng cách join qua bảng này, nên
-- index theo gym_branch_id là đường đọc nóng nhất.
create table ticket_type_branches (
    id bigint not null auto_increment,
    ticket_type_id bigint not null,
    gym_branch_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_ttb_type_branch (ticket_type_id, gym_branch_id)
) engine = InnoDB;

create index idx_ttb_branch on ticket_type_branches (gym_branch_id);

alter table ticket_type_branches
    add constraint FK_ttb_type foreign key (ticket_type_id) references ticket_types (id);
alter table ticket_type_branches
    add constraint FK_ttb_branch foreign key (gym_branch_id) references gym_branches (id);
