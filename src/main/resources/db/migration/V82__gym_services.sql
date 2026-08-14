-- =====================================================================
-- V82: dịch vụ kèm vé (FT-07 "Service" của SRS, dựng lại TRÊN mô hình vé).
--
-- Khác bảng gym_services cũ (V81 đã drop): dịch vụ KHÔNG còn là thứ đặt lịch
-- độc lập. Nó là add-on tính tiền một lần, khách tick thêm lúc mua vé —
-- xông hơi, tủ đồ riêng, khăn, nước... Vé vẫn là đơn vị nghiệp vụ duy nhất.
--
-- Vì sao dịch vụ khai ở cấp GYM chứ không theo chi nhánh như ticket_types:
-- add-on là dịch vụ phụ, gym thường áp dụng đồng loạt; tách theo chi nhánh sẽ
-- thêm một bảng nối nữa mà chưa có nhu cầu. Cần lọc theo chi nhánh sau này thì
-- thêm gym_service_branches y hệt ticket_type_branches.
--
-- ticket_service_items SNAPSHOT tên + giá tại thời điểm mua, giống cách vé
-- snapshot unit_price: gym sửa giá dịch vụ ngày mai không được phép làm đổi số
-- tiền của vé đã bán, và cũng không được làm sổ cái ví lệch.
-- =====================================================================

create table gym_services (
    id bigint not null auto_increment,
    gym_profile_id bigint not null,
    name varchar(150) not null,
    description varchar(1000),
    price decimal(12,2) not null,
    status varchar(20) not null default 'PUBLISHED',
    active bit not null default 1,
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_gym_services_gym on gym_services (gym_profile_id, status);

alter table gym_services
    add constraint FK_gym_services_gym foreign key (gym_profile_id) references gym_profiles (id);

-- Dòng dịch vụ đã mua kèm một vé. Không có FK ON DELETE CASCADE: vé không bao
-- giờ bị xoá cứng (chỉ đổi trạng thái), nên xoá tầng ở đây là bẫy chứ không
-- phải tiện ích.
create table ticket_service_items (
    id bigint not null auto_increment,
    ticket_id bigint not null,
    gym_service_id bigint not null,
    name varchar(150) not null,
    price decimal(12,2) not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_tsi_ticket_service (ticket_id, gym_service_id)
) engine = InnoDB;

create index idx_tsi_ticket on ticket_service_items (ticket_id);

alter table ticket_service_items
    add constraint FK_tsi_ticket foreign key (ticket_id) references tickets (id);
alter table ticket_service_items
    add constraint FK_tsi_service foreign key (gym_service_id) references gym_services (id);

-- Tổng tiền dịch vụ đã snapshot, cộng vào total_amount TRƯỚC voucher/điểm.
-- Vé cũ chưa có dịch vụ nên mặc định 0 là đúng nghĩa, không phải giá trị tạm.
alter table tickets
    add column services_amount decimal(12,2) not null default 0.00 after total_amount;
