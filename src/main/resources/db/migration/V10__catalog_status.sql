-- =====================================================================
-- V10 (UC-027): Vòng đời hiển thị của dịch vụ/gói tập trên marketplace
-- (PUBLISHED/HIDDEN/PAUSED/ARCHIVED). Backfill từ cờ active hiện có.
-- =====================================================================

alter table gym_services
    add column status enum ('ARCHIVED','HIDDEN','PAUSED','PUBLISHED') not null default 'PUBLISHED';

update gym_services set status = if(active = 1, 'PUBLISHED', 'HIDDEN');

alter table training_packages
    add column status enum ('ARCHIVED','HIDDEN','PAUSED','PUBLISHED') not null default 'PUBLISHED';

update training_packages set status = if(active = 1, 'PUBLISHED', 'HIDDEN');
