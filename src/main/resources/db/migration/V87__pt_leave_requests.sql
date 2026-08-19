-- =====================================================================
-- V87: đơn xin nghỉ / báo bận của PT.
--
-- Quyết định §4.2: đơn PHẢI được Gym duyệt mới có hiệu lực — Gym mới là chủ
-- lịch, cho phép PT tự khoá slot thì §4.1 (bảo vệ buổi khách đã đặt) mất nghĩa.
--
-- Quyết định §4.1: đơn đè lên buổi SCHEDULED không bị chặn duyệt; thay vào đó
-- PT phải nộp trước N giờ (system_configs 'pt.leave.min-lead-hours', V89) và
-- khi duyệt thì mỗi buổi vướng sinh một dòng session_pt_cancellations (V88)
-- để KHÁCH chọn đổi PT hay nhận hoàn phụ phí PT của ngày đó.
--
-- gym_profile_id denormalize từ pt_profiles: màn duyệt đơn của Gym lọc theo
-- (gym, status) liên tục, không được join qua pt_profiles cho mỗi lần mở.
-- =====================================================================

create table pt_leave_requests (
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    gym_profile_id bigint not null,
    -- LeaveType: LEAVE | SICK | BUSY | OTHER
    type varchar(20) not null,
    -- LeaveScope: FULL_DAY | SHIFT | TIME_RANGE
    scope varchar(20) not null,
    from_date date not null,
    to_date date not null,
    -- Chỉ scope = TIME_RANGE mới dùng; scope = SHIFT dùng bảng con bên dưới.
    start_time time,
    end_time time,
    reason varchar(1000) not null,
    attachment_url varchar(500),
    -- LeaveStatus: PENDING | APPROVED | REJECTED | CANCELLED
    status varchar(20) not null,
    reviewed_by varchar(255),
    reviewed_at datetime(6),
    reject_reason varchar(1000),
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

-- PtSlotValidator + kiểm chồng đơn: "PT P có đơn APPROVED nào phủ ngày D".
create index idx_pt_leave_pt_range on pt_leave_requests (pt_profile_id, status, from_date, to_date);
-- Màn duyệt đơn của Gym + badge đếm số đơn PENDING.
create index idx_pt_leave_gym_status on pt_leave_requests (gym_profile_id, status, created_at);

alter table pt_leave_requests
    add constraint FK_pt_leave_pt foreign key (pt_profile_id) references pt_profiles (id);
alter table pt_leave_requests
    add constraint FK_pt_leave_gym foreign key (gym_profile_id) references gym_profiles (id);

-- Bảng con vì phạm vi "một hoặc nhiều ca cụ thể" là yêu cầu nghiệp vụ: PT nghỉ
-- ca sáng và ca tối nhưng vẫn dạy ca chiều là chuyện bình thường.
create table pt_leave_request_shifts (
    leave_request_id bigint not null,
    gym_shift_id bigint not null,
    primary key (leave_request_id, gym_shift_id)
) engine = InnoDB;

alter table pt_leave_request_shifts
    add constraint FK_leave_shift_leave foreign key (leave_request_id) references pt_leave_requests (id);
alter table pt_leave_request_shifts
    add constraint FK_leave_shift_shift foreign key (gym_shift_id) references gym_shifts (id);
