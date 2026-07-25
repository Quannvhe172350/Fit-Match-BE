-- Mật khẩu bootstrap của super_admin (V41) đã bị lộ dạng plaintext trong comment
-- của chính file migration đó (nằm trong git history). Migration này xoay vòng
-- mật khẩu bằng hash mới, CHỈ khi tài khoản vẫn còn dùng đúng hash cũ đã lộ
-- (nếu admin đã tự đổi mật khẩu thì không đụng vào).
--
-- Mật khẩu mới KHÔNG được ghi ở đây — được bàn giao riêng cho người vận hành.
-- Sau khi đăng nhập lần đầu bằng mật khẩu mới, BẮT BUỘC đổi mật khẩu qua API
-- (PUT /api/auth/change-password) — thao tác này đồng thời thu hồi mọi JWT cũ
-- nhờ tokenVersion.
update users
set password_hash = '$2b$10$7TL6aTLxS2Y/IhyWGFaHguvyCB60uYrlsGRDQRfAcyPywKuExdff2',
    token_version = token_version + 1,
    updated_at = utc_timestamp()
where username = 'super_admin'
  and password_hash = '$2a$10$/RFEShTrN/A5l1dwelwZI.xGQyu8dWo9LA8Q88BWqIg3yIWMKro2a';
