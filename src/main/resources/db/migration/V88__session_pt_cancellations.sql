-- =====================================================================
-- V88 (quyết định §4.1): buổi tập bị PT nghỉ — chờ KHÁCH quyết.
--
-- Khi Gym duyệt đơn nghỉ đè lên buổi SCHEDULED, hệ thống KHÔNG huỷ buổi và
-- KHÔNG đổi SessionStatus. Vé có giá trị CẢ NGÀY (xem TrainingSession javadoc)
-- nên khách vẫn vào tập được — chỉ mất PT. Buổi giữ nguyên SCHEDULED, PT bị gỡ
-- khỏi buổi (trả slot về lưới), và dòng dưới đây ghi lại "còn một quyết định
-- treo": khách chọn PT thay thế (PUT /api/tickets/sessions/{id}/pt) hoặc nhận
-- hoàn phụ phí PT của đúng ngày đó.
--
-- Nhờ vậy SessionStatus giữ nguyên ba giá trị — enum đó cố ý rất hẹp, thêm
-- giá trị thứ tư sẽ lan vào SessionLifecycle, session_status_history, mọi bộ
-- lọc FE và cả logic markUsedUpIfComplete (đếm buổi để giải ngân).
--
-- KHÔNG unique theo training_session_id: khách đổi sang PT khác rồi PT thay
-- thế cũng xin nghỉ là kịch bản có thật. Ràng buộc "mỗi buổi chỉ một dòng
-- PENDING_CUSTOMER" kiểm ở tầng service.
-- =====================================================================

create table session_pt_cancellations (
    id bigint not null auto_increment,
    training_session_id bigint not null,
    leave_request_id bigint not null,
    -- Snapshot PT và khung giờ đã bị gỡ: buổi đã xoá pt_profile_id nên nếu
    -- không chép lại thì màn hình của khách không nói được "PT nào nghỉ".
    former_pt_profile_id bigint not null,
    former_slot_start time not null,
    former_slot_end time,
    -- PtCancellationStatus: PENDING_CUSTOMER | REPLACED | REFUNDED
    status varchar(20) not null,
    refund_amount decimal(12,2),
    resolved_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine = InnoDB;

create index idx_session_ptcancel_session on session_pt_cancellations (training_session_id, status);
-- Job tự hoàn tiền cho buổi khách quên xử lý quét theo trạng thái này.
create index idx_session_ptcancel_pending on session_pt_cancellations (status, created_at);

alter table session_pt_cancellations
    add constraint FK_ptcancel_session foreign key (training_session_id) references training_sessions (id);
alter table session_pt_cancellations
    add constraint FK_ptcancel_leave foreign key (leave_request_id) references pt_leave_requests (id);
alter table session_pt_cancellations
    add constraint FK_ptcancel_pt foreign key (former_pt_profile_id) references pt_profiles (id);
