-- =====================================================================
-- V78 (P4 — câu 24): phân công PT chỉ còn một đích duy nhất là CHI NHÁNH.
--
-- Mô hình cũ cho gán PT vào dịch vụ / gói tập / chi nhánh và service layer
-- phải tự bảo đảm "đúng một trong ba". Mô hình vé không có dịch vụ lẫn gói
-- nữa, nên hai đích kia không còn nghĩa gì — PtSlotValidator chỉ hỏi đúng
-- một câu: "PT này có phụ trách chi nhánh của vé không?".
-- =====================================================================

-- Dọn các dòng phân công không gắn chi nhánh trước khi siết NOT NULL —
-- chúng trỏ vào dịch vụ/gói sắp bị drop ở V81 và không thể suy ra chi nhánh.
delete from pt_assignments where gym_branch_id is null;

alter table pt_assignments
    drop foreign key FK_pt_assignments_service;
alter table pt_assignments
    drop foreign key FK_pt_assignments_package;

alter table pt_assignments
    drop index uk_pt_assignment_service;
alter table pt_assignments
    drop index uk_pt_assignment_package;

alter table pt_assignments
    drop column gym_service_id,
    drop column training_package_id;

alter table pt_assignments
    modify column gym_branch_id bigint not null;
