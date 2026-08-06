-- =====================================================================
-- V60 (UC-003): đăng nhập bằng Google.
-- Lưu claim `sub` của Google thay vì chỉ dựa vào email: `sub` không đổi khi
-- người dùng đổi địa chỉ email Google, còn email thì có thể chuyển sang tài
-- khoản khác. UNIQUE để một tài khoản Google không liên kết được vào 2 user.
--
-- KHÔNG bỏ NOT NULL của password_hash: user tạo từ Google nhận một hash ngẫu
-- nhiên (không ai biết mật khẩu gốc) nên vẫn không thể đăng nhập bằng mật khẩu,
-- và mọi truy vấn/ràng buộc hiện có giữ nguyên hành vi.
-- =====================================================================
alter table users
    add column google_id varchar(64) null after password_hash,
    add constraint uk_users_google_id unique (google_id);
