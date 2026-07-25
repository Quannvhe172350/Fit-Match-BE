# FitMatch Backend

Marketplace kết nối Khách hàng – Phòng gym – PT. Spring Boot 3.3.5 · Java 21 · MariaDB · Flyway · JWT · VietQR + Casso webhook · GCS/local storage.

## Chạy nhanh nhất (Docker Compose — cả DB + BE + FE)

```bash
# tại thư mục cha (chứa cả Fit-Match-BE và Fit-Match-FE)
docker compose up -d --build
# FE: http://localhost:3000 — BE: http://localhost:8080 — Swagger: http://localhost:8080/swagger-ui.html
```

## Chạy thủ công (dev)

Yêu cầu: JDK 21+, MariaDB đang chạy ở `localhost:3306`.

```bash
# Cách 1: profile local (khuyến nghị) — cần src/main/resources/application-local.yml
#         (git-ignored; xin file mẫu từ team hoặc tự tạo dựa trên .env.example)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Cách 2: profile mặc định — bắt buộc export JWT_SECRET (fail-fast nếu thiếu)
export JWT_SECRET=$(openssl rand -base64 48)
./mvnw spring-boot:run
```

- Port: **8080** (mọi profile). Flyway tự migrate `V1 → V46` khi khởi động (`ddl-auto: validate` — schema do Flyway quản lý, **không sửa bảng tay**, muốn đổi schema thì thêm `V47__*.sql`).
- Profile `local` tự seed 5 tài khoản: `admin|moderator|finance|operator|customer@fitmatch.local` — mật khẩu chung `Password123!`.
- Không cấu hình `MAIL_HOST` → email chỉ được log ra console (LoggingEmailService).
- Không cấu hình `SMS_PROVIDER` → OTP SMS chỉ được log ra console (`[SMS-STUB]`). Gửi thật: `SMS_PROVIDER=twilio` (trial miễn phí — hợp đồ án/demo, chỉ gửi tới số đã verify) hoặc `SMS_PROVIDER=esms` (esms.vn, production VN). Xem `.env.example`.
- `GCS_ENABLED=false` → file upload lưu local `./uploads`.

## Biến môi trường

Xem đầy đủ trong [.env.example](.env.example). Bắt buộc ở prod: `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `CASSO_WEBHOOK_SECRET`, `VIETQR_ACCOUNT_NO/NAME` (không có Casso secret thì **mọi webhook thanh toán bị từ chối**).

## Test

```bash
./mvnw test                          # unit tests (mock-based)
# Integration test migration (cần MariaDB thật):
IT_DB_URL=jdbc:mariadb://localhost:3306 IT_DB_USERNAME=root IT_DB_PASSWORD=root123 ./mvnw test -Dtest=FlywayMigrationTest
```

## Deploy

CI/CD qua GitHub Actions (`.github/workflows/cd.yml`): push lên `develop` → build image → GHCR → deploy GCE VM. Toàn bộ secret cấu hình trong GitHub Secrets của repo (xem danh sách trong workflow). Tài khoản bootstrap prod `super_admin` — **đổi mật khẩu ngay sau lần đăng nhập đầu** (`PUT /api/auth/change-password`).

## Kiến trúc

`controller → service (interface/impl/support) → repository → entity`; DTO validation bằng jakarta + `@StrongPassword`; state machine booking tập trung (`BookingLifecycle`); ví escrow 4 bucket (`held/pending/available/frozen`) với pessimistic lock + `@Version`; 3 scheduler idempotent (expire payment order 5', release settlement 15', auto no-show 15'); audit log 46 action; thông báo in-app + email phát sau commit (`@TransactionalEventListener`).

Báo cáo rà soát toàn diện: `../BAO-CAO-RA-SOAT-DU-AN.md`.
