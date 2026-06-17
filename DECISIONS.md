# DECISIONS.md — Nhật ký quyết định & lỗi đã xử lý

Tài liệu này ghi lại **mọi sai lệch so với đặc tả gốc** (`usecase.xlsx`), **lỗi/lỗ hổng phát hiện trong code**, và **quyết định thiết kế** trong quá trình phát triển FitMatch BE. Mỗi mục nêu: *vấn đề → giải thích → cách xử lý*.

---

## 0. Quyết định nền tảng (chốt với chủ dự án 2026-06-24)

| # | Quyết định | Lý do |
|---|---|---|
| D-01 | **Không dùng công cụ migration (Flyway/Liquibase).** Giữ Hibernate `ddl-auto: update` (dev) / `validate` (prod). | Chủ dự án chọn giữ hiện trạng. ⚠️ Ràng buộc "mọi schema change phải kèm migration" được **nới**: thay vào đó, mỗi entity mới đều ghi chú schema trong commit body và schema do Hibernate sinh. Khi lên production nên cân nhắc bổ sung Flyway baseline. |
| D-02 | **Payment Gateway & Email dùng stub có abstraction.** Tạo interface `PaymentGateway`, `EmailService` + impl giả lập (log/auto-success). | Cho phép code chạy & test ngay; sau cắm VNPay/MoMo/SMTP thật chỉ cần thay impl, không đụng business logic. |
| D-03 | **Triển khai tự động theo module, foundation trước** (A→Q), mỗi UC một commit Conventional Commits, báo cáo sau mỗi module. | Chủ dự án chọn nhịp này. |
| D-04 | **Phân trang chuẩn hoá** bằng `PageResponse<T>` + Spring `Pageable` cho mọi list endpoint. Spec gốc không đề cập phân trang. | REST best practice, tránh trả toàn bộ bảng. |
| D-05 | **Bật method-level security** (`@EnableMethodSecurity`) và phân quyền bằng `@PreAuthorize` theo actor của từng UC. | Spec yêu cầu phân quyền theo actor; `anyRequest().authenticated()` ban đầu không đủ. |

---

## 1. Lỗi/lỗ hổng phát hiện trong code hiện có

| # | UC | Mức độ | Vấn đề | Xử lý |
|---|---|---|---|---|
| B-01 | UC-01 | 🔴 Critical | `RegisterRequest.role` cho phép client **tự đăng ký `ROLE_ADMIN`/`ROLE_GYM_OPERATOR`** → privilege escalation. | Bỏ field `role` khỏi self-register; mặc định `ROLE_CUSTOMER`. PT/Gym nâng cấp qua onboarding (UC-23/41); Admin gán role (UC-12). |
| B-02 | UC-05 | 🟠 Major | `updateProfile` đổi `email` **không kiểm tra trùng** → vi phạm unique constraint trả HTTP 500 thay vì 409. | Thêm kiểm tra `existsByEmail` (loại trừ chính mình) → ném `EMAIL_EXISTS` (409). |
| B-03 | — | 🟠 Major | Swagger **thiếu security scheme JWT** → nút *Authorize* không dùng được; chưa có `@Operation`/`@ApiResponse`. | Thêm `bearerAuth` scheme vào `SwaggerConfig` + annotate controller. |
| B-04 | UC-03 | 🟡 Minor | `logout` là no-op (stateless JWT) — token vẫn hợp lệ tới khi hết hạn. | Giữ nguyên (chấp nhận với JWT stateless); ghi chú có thể thêm blacklist Redis sau. |
| B-05 | — | 🟡 Minor | JWT secret **hardcode** trong `application.yml` (đã commit). | prod đã dùng `${JWT_SECRET}`. Giữ secret dev cho tiện local; khuyến nghị chuyển sang env cả ở dev. |

---

## 2. Sai lệch / bất nhất trong đặc tả use case (usecase.xlsx)

Phần Precondition/Postcondition trong xlsx được **sinh theo khuôn mẫu** nên nhiều chỗ sai logic. Các hiệu chỉnh:

| # | UC | Spec gốc (sai) | Hành vi đúng đã áp dụng |
|---|---|---|---|
| S-01 | UC-08 | "Reserved / Missing in source baseline" (ô trống) | **Bỏ qua** theo xác nhận — không tự bịa scope. |
| S-02 | UC-05 | Postcondition: "Requested data is displayed without changing system records" | Mâu thuẫn với hành vi *Update*. Hiệu chỉnh: hồ sơ được cập nhật & lưu, trả về bản ghi mới. |
| S-03 | UC-29 / UC-45 | Postcondition: "related balance/status is updated" cho hành vi *Review* (chỉ xem) | *Review* chỉ đọc hồ sơ verification; việc đổi trạng thái thuộc UC-30/46 (Approve/Reject). |
| S-04 | UC-02/03/04/06 | Postcondition chung chung "System state reflects the completed use case" | Diễn giải theo từng hành vi cụ thể (login cấp token, logout, reset mật khẩu...). |

*(Bảng này được cập nhật tiếp khi phát hiện thêm trong các module sau.)*
