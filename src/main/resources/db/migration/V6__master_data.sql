-- =====================================================================
-- V6 (UC-078): Master data — danh mục dịch vụ và cấu hình hệ thống.
-- =====================================================================

create table service_categories (
    active bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(100) not null,
    created_by varchar(255),
    updated_by varchar(255),
    description varchar(500),
    primary key (id)
) engine=InnoDB;

alter table service_categories
    add constraint UK_service_categories_name unique (name);

create table system_configs (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    config_key varchar(100) not null,
    created_by varchar(255),
    updated_by varchar(255),
    description varchar(500),
    config_value varchar(1000) not null,
    primary key (id)
) engine=InnoDB;

alter table system_configs
    add constraint UK_system_configs_key unique (config_key);
