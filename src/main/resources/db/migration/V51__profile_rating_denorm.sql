-- =====================================================================
-- V51 (UC-008): denormalize điểm đánh giá lên gym_profiles/pt_profiles để
-- marketplace sort được theo rating (Pageable sort=avgRating,desc) mà không
-- phải aggregate reviews mỗi request. Nguồn sự thật vẫn là bảng reviews —
-- RatingAggregator.refresh* cập nhật 2 cột này sau mỗi thay đổi review
-- (create/update/delete/moderate).
-- =====================================================================

alter table gym_profiles
    add column avg_rating decimal(3, 2) not null default 0,
    add column rating_count int not null default 0;

alter table pt_profiles
    add column avg_rating decimal(3, 2) not null default 0,
    add column rating_count int not null default 0;

-- Backfill từ review VISIBLE hiện có
update gym_profiles g
set g.avg_rating = coalesce((select round(avg(r.rating), 2) from reviews r
                             where r.gym_profile_id = g.id and r.status = 'VISIBLE'), 0),
    g.rating_count = (select count(*) from reviews r
                      where r.gym_profile_id = g.id and r.status = 'VISIBLE');

update pt_profiles p
set p.avg_rating = coalesce((select round(avg(r.rating), 2) from reviews r
                             where r.pt_profile_id = p.id and r.status = 'VISIBLE'), 0),
    p.rating_count = (select count(*) from reviews r
                      where r.pt_profile_id = p.id and r.status = 'VISIBLE');
