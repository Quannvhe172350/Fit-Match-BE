-- Bootstrap tài khoản ADMIN đầu tiên cho production. DevDataSeeder (Java) chỉ
-- chạy ở profile 'local' nên trước migration này, production không có bất kỳ
-- tài khoản admin nào để đăng nhập lần đầu.
--
-- Flyway migration chạy ở MỌI profile (không có khái niệm Spring profile),
-- nên username/email cố tình KHÁC với các tài khoản seed của DevDataSeeder
-- (admin/moderator/finance/operator/customer @fitmatch.local) để không đụng
-- độ khi migration này chạy trên DB local/dev.
--
-- Mật khẩu khởi tạo: SuperAdmin@FitMatch2026! (hash BCrypt bên dưới, strength 10,
-- cùng thuật toán với PasswordEncoder của app — xem SecurityConfig).
--
-- BẮT BUỘC: đăng nhập ngay sau khi deploy production và đổi mật khẩu (+ tạo
-- tài khoản admin thật, vô hiệu hoá tài khoản này) qua API thật. Đây chỉ là
-- tài khoản bootstrap dùng một lần.
insert into users (
    username, email, password_hash, full_name, role, status,
    email_verified, created_at
)
select 'super_admin', 'admin@fitmatch.com',
       '$2a$10$/RFEShTrN/A5l1dwelwZI.xGQyu8dWo9LA8Q88BWqIg3yIWMKro2a',
       'Super Admin', 'ROLE_ADMIN', 'ACTIVE', 1, utc_timestamp()
where not exists (
    select 1 from users where username = 'super_admin' or email = 'admin@fitmatch.com'
);
