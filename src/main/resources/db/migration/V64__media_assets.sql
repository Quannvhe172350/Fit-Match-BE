-- =====================================================================
-- V64: Media dùng chung cho toàn hệ thống (avatar, ảnh gym/chi nhánh,
-- dịch vụ, gói tập, check-in, PT, đánh giá).
--
-- Nội dung file KHÔNG nằm ở đây: binary được đẩy lên Google Cloud Storage,
-- bảng này chỉ giữ storage_key + bucket để dựng lại URL (public hoặc signed).
-- Quan hệ tới entity nghiệp vụ là polymorphic (entity_type + entity_id) nên
-- thêm chỗ dùng ảnh mới không cần migration.
--
-- entity_id NULL = ảnh nháp: đã upload nhưng chưa gắn vào bản ghi nào (khách
-- chọn ảnh trước khi bấm "Gửi đánh giá"). Job dọn rác xoá sau 24h.
-- =====================================================================

create table media_assets (
    id bigint not null auto_increment,
    entity_type varchar(20) not null,
    entity_id bigint null,
    image_type varchar(20) not null,
    storage_key varchar(500) not null,
    bucket_name varchar(255) null,
    thumbnail_key varchar(500) null,
    original_name varchar(255) null,
    mime_type varchar(100) not null,
    file_size bigint not null default 0,
    width int null,
    height int null,
    url varchar(1000) null,
    caption varchar(255) null,
    sort_order int not null default 0,
    is_primary tinyint(1) not null default 0,
    owner_user_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    -- Truy vấn nóng nhất: "ảnh của entity X, loại Y, theo thứ tự".
    key idx_media_entity (entity_type, entity_id, image_type, sort_order),
    key idx_media_owner (owner_user_id),
    key idx_media_created_at (created_at),
    constraint fk_media_owner foreign key (owner_user_id) references users (id)
) engine = InnoDB;

-- ---------------------------------------------------------------------
-- Chuyển ảnh gym/chi nhánh cũ (gym_media, UC-016) sang hệ thống mới.
-- Bảng gym_media được GIỮ NGUYÊN (không drop) để rollback được: chỉ cần
-- revert code là các API đọc lại bảng cũ, dữ liệu vẫn còn nguyên vẹn.
--
-- storage_key được suy ra từ URL đã lưu:
--   https://storage.googleapis.com/<bucket>/<key>  -> <key>
--   http://host:port/api/files/<folder>/<file>     -> <folder>/<file>
-- URL lạ (CDN ngoài) thì giữ nguyên cả URL làm key và để url không đổi —
-- ảnh vẫn hiển thị được, chỉ là không xoá được object phía storage.
-- ---------------------------------------------------------------------
insert into media_assets (entity_type, entity_id, image_type, storage_key, url, caption,
                          mime_type, file_size, sort_order, is_primary, owner_user_id,
                          created_at, updated_at, created_by, updated_by)
select
    case when m.gym_branch_id is null then 'GYM' else 'BRANCH' end,
    coalesce(m.gym_branch_id, m.gym_profile_id),
    'GALLERY',
    case
        when m.url like 'https://storage.googleapis.com/%'
            then substring(m.url, length(substring_index(m.url, '/', 4)) + 2)
        when locate('/api/files/', m.url) > 0
            then substring(m.url, locate('/api/files/', m.url) + 11)
        else m.url
    end,
    m.url,
    m.caption,
    case
        when lower(m.url) like '%.png'  then 'image/png'
        when lower(m.url) like '%.webp' then 'image/webp'
        when lower(m.url) like '%.gif'  then 'image/gif'
        else 'image/jpeg'
    end,
    0,
    0,
    0,
    g.user_id,
    m.created_at,
    m.updated_at,
    m.created_by,
    m.updated_by
from gym_media m
join gym_profiles g on g.id = m.gym_profile_id;

-- ---------------------------------------------------------------------
-- Avatar người dùng đang lưu ở users.avatar_url (chuỗi URL tự do). Cột đó
-- được GIỮ LẠI làm cache đọc nhanh — MediaService cập nhật nó mỗi khi avatar
-- đổi — nên không backfill ở đây: avatar cũ vẫn hiển thị bình thường, ảnh
-- mới upload sẽ có thêm bản ghi media_assets.
-- ---------------------------------------------------------------------
