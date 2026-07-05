-- =====================================================================
-- V7 (UC-024): Dịch vụ gắn danh mục master data và điều kiện tham gia.
-- =====================================================================

alter table gym_services
    add column category_id bigint null;

alter table gym_services
    add constraint FK_gym_services_category
    foreign key (category_id) references service_categories (id);

alter table gym_services
    add column eligibility_notes varchar(1000) null;
