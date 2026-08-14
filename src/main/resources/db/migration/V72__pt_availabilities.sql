-- =====================================================================
-- V72 (mô hình vé — câu 26): lịch rảnh của PT theo NGÀY CỤ THỂ, thay
-- availability_slots (lặp theo thứ trong tuần).
--
-- Hệ quả: blocked_times trở thành thừa — PT bận thì đơn giản là không khai
-- khung giờ cho ngày đó. Bảng cũ được drop ở P4.
--
-- Unique (pt_profile_id, slot_date, start_time) chặn khai trùng khung; khách
-- đặt vào khung nào thì training_sessions giữ (pt_slot_start, pt_slot_end) —
-- hai bảng không FK với nhau, PtSlotValidator đối chiếu bằng giá trị giờ.
-- =====================================================================

create table pt_availabilities (
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    slot_date date not null,
    start_time time not null,
    end_time time not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_pt_avail_slot (pt_profile_id, slot_date, start_time)
) engine = InnoDB;

create index idx_pt_avail_pt_date on pt_availabilities (pt_profile_id, slot_date);
-- Tìm PT rảnh theo khung giờ (chọn giờ trước rồi lọc PT) quét theo ngày.
create index idx_pt_avail_date_time on pt_availabilities (slot_date, start_time);

alter table pt_availabilities
    add constraint FK_pt_avail_pt foreign key (pt_profile_id) references pt_profiles (id);
