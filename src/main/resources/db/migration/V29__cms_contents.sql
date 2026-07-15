-- =====================================================================
-- V29 (UC-074): nội dung CMS công khai & chiến dịch nổi bật.
-- =====================================================================

create table cms_contents (
    id bigint not null auto_increment,
    type varchar(20) not null,
    title varchar(200) not null,
    body text null,
    image_url varchar(500) null,
    link varchar(500) null,
    sort_order int not null default 0,
    published bit not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_cms_type_pub (type, published, sort_order)
) engine = InnoDB;
