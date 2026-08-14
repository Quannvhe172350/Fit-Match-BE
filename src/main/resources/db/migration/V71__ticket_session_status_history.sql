-- =====================================================================
-- V71 (mô hình vé): audit vòng đời. HAI bảng riêng vì vé và buổi tập là hai
-- state machine độc lập — vé nói về tiền, buổi nói về lịch. Gộp chung sẽ phải
-- nhét hai tập enum vào một cột và mất khả năng lọc theo từng dòng đời.
--
-- Cả hai bảng cũng nhận dòng "không đổi trạng thái" (from = to) do
-- recordNote(...) ghi khi dời lịch / đổi khung PT / bổ sung PT — nguyên tắc
-- mọi thay đổi đều có dấu vết.
-- =====================================================================

create table ticket_status_history (
    id bigint not null auto_increment,
    ticket_id bigint not null,
    from_status varchar(20),
    to_status varchar(20) not null,
    reason varchar(500),
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_tsh_ticket on ticket_status_history (ticket_id);

alter table ticket_status_history
    add constraint FK_tsh_ticket foreign key (ticket_id) references tickets (id);

create table session_status_history (
    id bigint not null auto_increment,
    session_id bigint not null,
    from_status varchar(20),
    to_status varchar(20) not null,
    reason varchar(500),
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_ssh_session on session_status_history (session_id);

alter table session_status_history
    add constraint FK_ssh_session foreign key (session_id) references training_sessions (id);
