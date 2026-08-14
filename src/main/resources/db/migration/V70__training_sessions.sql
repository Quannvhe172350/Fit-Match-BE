-- =====================================================================
-- V70 (mô hình vé): một ngày tập của một vé.
--
-- session_date là DATE chứ không phải DATETIME — vé có giá trị CẢ NGÀY, khách
-- vào lúc nào cũng được. Giờ giấc chỉ tồn tại khi có PT (pt_slot_start/end),
-- và chỉ ràng buộc lịch của PT chứ không ràng buộc lịch của phòng gym.
--
-- gym_branch_id là bản sao từ vé: lịch quản lý của gym đọc theo
-- (chi nhánh, khoảng ngày) và không được phép join ngược lên tickets cho mỗi ô.
--
-- checked_in_at: khách tự check-in, CHỈ áp dụng cho buổi có PT. Đây là ghi
-- nhận có mặt, không ảnh hưởng status/tiền — buổi vẫn tiêu theo ngày (câu 9).
-- pt_confirmed_at/by + evidence_url: gym xác nhận PT có đến, kèm ảnh (câu 31/33).
-- =====================================================================

create table training_sessions (
    id bigint not null auto_increment,
    ticket_id bigint not null,
    gym_branch_id bigint not null,
    day_index int not null,
    session_date date not null,
    pt_profile_id bigint,
    pt_slot_start time,
    pt_slot_end time,
    status varchar(20) not null,
    status_reason varchar(500),
    checked_in_at datetime(6),
    pt_confirmed_at datetime(6),
    pt_confirmed_by varchar(255),
    evidence_url varchar(500),
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    -- Một vé không thể có hai buổi cùng day_index; chặn double-submit lúc đặt lịch.
    unique key uk_session_ticket_day (ticket_id, day_index)
) engine = InnoDB;

create index idx_sessions_ticket on training_sessions (ticket_id, day_index);
-- Lịch quản lý của gym: luôn hỏi theo chi nhánh + khoảng ngày đang xem.
create index idx_sessions_branch_date on training_sessions (gym_branch_id, session_date);
-- PtSlotValidator hỏi "PT này ngày này đã bị đặt khung nào" trên mỗi lần đặt lịch.
create index idx_sessions_pt_date on training_sessions (pt_profile_id, session_date);
-- SessionCompletionJob quét buổi SCHEDULED đã qua ngày.
create index idx_sessions_status_date on training_sessions (status, session_date);

alter table training_sessions
    add constraint FK_sessions_ticket foreign key (ticket_id) references tickets (id);
alter table training_sessions
    add constraint FK_sessions_branch foreign key (gym_branch_id) references gym_branches (id);
alter table training_sessions
    add constraint FK_sessions_pt foreign key (pt_profile_id) references pt_profiles (id);
