-- D-12 (audit 2026-07-17): startReview trước đây chỉ flip status, không lưu ai phụ trách
-- — hai moderator có thể cùng xử lý một case mà không thấy nhau.
ALTER TABLE disputes
    ADD COLUMN assigned_moderator VARCHAR(50) NULL AFTER moderator_note;

CREATE INDEX idx_disputes_assignee ON disputes (assigned_moderator);
