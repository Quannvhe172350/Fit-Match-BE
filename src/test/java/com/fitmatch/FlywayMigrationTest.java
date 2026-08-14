package com.fitmatch;

import com.fitmatch.common.enums.WalletTxnType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test chạy TOÀN BỘ migration (V1 -> V36) trên MariaDB thật rồi
 * kiểm tra schema — lấp khoảng trống mà unit test mock-based không thấy được
 * (đặc biệt lớp bug P0-3: giá trị enum Java lệch cột ENUM DB, chỉ lộ khi
 * INSERT/định nghĩa cột).
 *
 * <p>Guard bằng biến môi trường {@code IT_DB_URL} nên {@code mvn test} thường
 * (không có DB) sẽ BỎ QUA — CI/máy không DB không vỡ. Chạy thật:
 * <pre>
 *   IT_DB_URL=jdbc:mariadb://localhost:3306/ IT_DB_USER=root IT_DB_PASSWORD=xxx \
 *       mvnw test -Dtest=FlywayMigrationTest
 * </pre>
 * Test tạo schema tạm {@code fitmatch_flyway_it} và xóa sau khi chạy — không
 * đụng vào DB dev.
 */
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class FlywayMigrationTest {

    private static final String SCHEMA = "fitmatch_flyway_it";
    private static String server;
    private static String user;
    private static String password;

    @BeforeAll
    static void createSchema() throws SQLException {
        server = System.getenv("IT_DB_URL");
        user = System.getenv().getOrDefault("IT_DB_USER", "root");
        password = System.getenv().getOrDefault("IT_DB_PASSWORD", "");
        try (Connection c = DriverManager.getConnection(server, user, password);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + SCHEMA);
            s.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(server, user, password);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    private static String schemaUrl() {
        return (server.endsWith("/") ? server : server + "/") + SCHEMA;
    }

    @Test
    void allMigrationsApplyCleanlyAndSchemaMatchesEntities() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(schemaUrl(), user, password)
                .locations("classpath:db/migration")
                .load();
        var result = flyway.migrate();
        assertThat(result.success).as("Flyway migrate should succeed").isTrue();
        assertThat(flyway.info().current().getVersion().getVersion())
                .as("latest applied migration version").isEqualTo("81");

        try (Connection c = DriverManager.getConnection(schemaUrl(), user, password)) {
            // --- P0-3 guard: MỌI giá trị WalletTxnType phải có trong cột ENUM ---
            // (bắt được cả trường hợp thêm enum Java mới mà quên migration về sau).
            String walletType = columnType(c, "wallet_transactions", "type");
            for (WalletTxnType t : WalletTxnType.values()) {
                assertThat(walletType)
                        .as("wallet_transactions.type ENUM must contain " + t.name())
                        .contains("'" + t.name() + "'");
            }

            // --- V32/V33/V36: cột @Version optimistic lock ---
            for (String table : List.of("tickets", "training_sessions", "ticket_types", "disputes",
                    "refund_requests", "withdrawal_requests", "payment_orders", "gym_profiles")) {
                assertThat(hasColumn(c, table, "version"))
                        .as(table + ".version").isTrue();
            }

            // --- V61: ví đa chủ sở hữu + hạ tầng chi trả ---
            for (String column : List.of("owner_type", "user_id")) {
                assertThat(hasColumn(c, "wallets", column)).as("wallets." + column).isTrue();
            }
            // gym_profile_id phải nullable, nếu không thì không tạo được ví khách hàng.
            assertThat(isNullable(c, "wallets", "gym_profile_id"))
                    .as("wallets.gym_profile_id must be nullable").isTrue();

            for (String column : List.of("ref_code", "bank_bin", "qr_content", "paid_at", "auto_matched")) {
                assertThat(hasColumn(c, "withdrawal_requests", column))
                        .as("withdrawal_requests." + column).isTrue();
            }
            assertThat(hasColumn(c, "payment_transactions", "direction"))
                    .as("payment_transactions.direction").isTrue();
            assertThat(hasColumn(c, "payment_transactions", "withdrawal_request_id"))
                    .as("payment_transactions.withdrawal_request_id").isTrue();
            assertThat(hasColumn(c, "bank_accounts", "account_number")).as("bank_accounts").isTrue();

            // Master data ngân hàng phải có sẵn — thiếu BIN thì không sinh được QR payout.
            assertThat(rowCount(c, "banks")).as("seeded banks").isGreaterThan(30);

            // Mã đối soát backfill phải là DUY NHẤT, kể cả với dữ liệu cũ.
            assertThat(hasUniqueIndex(c, "withdrawal_requests", "ref_code"))
                    .as("withdrawal_requests.ref_code unique").isTrue();

            // --- V64: media dùng chung (ảnh nằm trên GCS, DB chỉ giữ metadata) ---
            for (String column : List.of("entity_type", "entity_id", "image_type", "storage_key",
                    "bucket_name", "thumbnail_key", "mime_type", "file_size", "width", "height",
                    "url", "sort_order", "is_primary", "owner_user_id")) {
                assertThat(hasColumn(c, "media_assets", column))
                        .as("media_assets." + column).isTrue();
            }
            // Ảnh nháp (chưa gắn entity) phải lưu được -> entity_id bắt buộc nullable.
            assertThat(isNullable(c, "media_assets", "entity_id"))
                    .as("media_assets.entity_id must be nullable for draft uploads").isTrue();
            // Không có index này thì mọi lần mở trang gym là full scan bảng ảnh.
            assertThat(hasIndex(c, "media_assets", "idx_media_entity"))
                    .as("media_assets composite lookup index").isTrue();
            assertThat(hasIndex(c, "media_assets", "idx_media_owner"))
                    .as("media_assets owner index").isTrue();
            // KHÔNG được có cột binary: nội dung ảnh phải nằm trên object storage.
            assertThat(columnType(c, "media_assets", "data")).as("media_assets must not store blobs").isNull();
            // gym_media giữ nguyên để rollback được — migration chỉ chép sang, không xoá.
            assertThat(hasColumn(c, "gym_media", "url")).as("legacy gym_media preserved").isTrue();

            // --- V67-V72: mô hình vé (P1 hoàn toàn additive, luồng booking cũ chưa đụng) ---
            // Hạn vé phải có sẵn đúng một dòng, nếu không thì không mua nổi vé đầu tiên.
            assertThat(rowCount(c, "platform_ticket_config")).as("seeded ticket expiry config").isEqualTo(1);

            // Catalog vé khai ở cấp gym rồi tick chọn chi nhánh qua bảng nối.
            assertThat(hasColumn(c, "ticket_types", "gym_profile_id")).as("ticket_types is gym-scoped").isTrue();
            assertThat(hasColumn(c, "ticket_types", "pt_surcharge_per_day"))
                    .as("ticket_types.pt_surcharge_per_day").isTrue();
            assertThat(hasColumn(c, "ticket_type_branches", "gym_branch_id"))
                    .as("ticket_type_branches join table").isTrue();

            for (String column : List.of("kind", "day_count", "with_pt", "unit_price",
                    "pt_surcharge_per_day", "payable_amount", "expires_at", "start_date",
                    "promo_released", "settlement_status", "commission_percent")) {
                assertThat(hasColumn(c, "tickets", column)).as("tickets." + column).isTrue();
            }

            // session_date PHẢI là DATE: vé có giá trị cả ngày, giờ chỉ tồn tại khi có PT.
            assertThat(columnType(c, "training_sessions", "session_date"))
                    .as("training_sessions.session_date must be a DATE").isEqualTo("date");
            for (String column : List.of("pt_profile_id", "pt_slot_start", "pt_slot_end",
                    "checked_in_at", "pt_confirmed_at", "evidence_url")) {
                assertThat(isNullable(c, "training_sessions", column))
                        .as("training_sessions." + column + " must be nullable (buổi không PT)").isTrue();
            }

            // Optimistic lock trên hai bảng mới có tranh chấp ghi đồng thời.
            for (String table : List.of("tickets", "training_sessions", "ticket_types")) {
                assertThat(hasColumn(c, table, "version")).as(table + ".version").isTrue();
            }

            // Hai state machine = hai bảng history riêng.
            assertThat(hasColumn(c, "ticket_status_history", "ticket_id")).isTrue();
            assertThat(hasColumn(c, "session_status_history", "session_id")).isTrue();

            // Lịch PT theo ngày cụ thể, không còn day_of_week.
            assertThat(columnType(c, "pt_availabilities", "slot_date"))
                    .as("pt_availabilities.slot_date must be a DATE").isEqualTo("date");
            assertThat(columnType(c, "pt_availabilities", "day_of_week"))
                    .as("pt_availabilities must not repeat weekly").isNull();

            // Index của các đường đọc nóng — thiếu là full scan mỗi lần mở lịch/chạy job.
            assertThat(hasIndex(c, "training_sessions", "idx_sessions_branch_date"))
                    .as("gym calendar range index").isTrue();
            assertThat(hasIndex(c, "training_sessions", "idx_sessions_pt_date"))
                    .as("PT slot lookup index").isTrue();
            assertThat(hasIndex(c, "tickets", "idx_tickets_expiry"))
                    .as("TicketExpiryJob index").isTrue();

            // --- V73: dòng tiền neo vào vé ---
            for (String table : List.of("payment_orders", "wallet_transactions", "loyalty_transactions")) {
                assertThat(hasColumn(c, table, "ticket_id")).as(table + ".ticket_id").isTrue();
            }
            // Một vé chỉ được có một đơn thanh toán.
            assertThat(hasUniqueIndex(c, "payment_orders", "ticket_id"))
                    .as("payment_orders.ticket_id unique").isTrue();

            // --- V74: hoàn tiền theo vé, có mức hoàn và số ngày đã qua ---
            for (String column : List.of("ticket_id", "refund_mode", "elapsed_days", "retained_amount")) {
                assertThat(hasColumn(c, "refund_requests", column))
                        .as("refund_requests." + column).isTrue();
            }

            // --- V75: tranh chấp neo vé + (tuỳ chọn) buổi tập ---
            for (String column : List.of("ticket_id", "session_id")) {
                assertThat(hasColumn(c, "disputes", column)).as("disputes." + column).isTrue();
            }
            // session_id NULL = tranh chấp cấp vé, nên PHẢI nullable.
            assertThat(isNullable(c, "disputes", "session_id"))
                    .as("disputes.session_id must be nullable (ticket-level dispute)").isTrue();

            // --- V76: đánh giá tách GYM / PT ---
            for (String column : List.of("target_type", "ticket_id", "session_id")) {
                assertThat(hasColumn(c, "reviews", column)).as("reviews." + column).isTrue();
            }
            // Mỗi vé một đánh giá gym, mỗi buổi một đánh giá PT. UNIQUE trên cột
            // nullable là đủ vì InnoDB cho phép nhiều dòng NULL.
            assertThat(hasUniqueIndex(c, "reviews", "ticket_id"))
                    .as("reviews.ticket_id unique").isTrue();
            assertThat(hasUniqueIndex(c, "reviews", "session_id"))
                    .as("reviews.session_id unique").isTrue();
            // Backfill: không được còn dòng review nào thiếu target_type.
            assertThat(countWhere(c, "reviews", "target_type is null"))
                    .as("legacy reviews backfilled to GYM").isZero();

            // --- V77-V81: mô hình booking đã biến mất hoàn toàn ---
            // Đây là chốt chặn thật của P4: ddl-auto=validate chỉ bắt được entity
            // thừa cột, KHÔNG bắt được bảng cũ còn sót lại trong schema.
            for (String table : List.of("bookings", "booking_status_history", "customer_packages",
                    "session_notes", "waitlist_entries", "blocked_times", "availability_slots",
                    "training_packages", "gym_services")) {
                assertThat(tableExists(c, table)).as("legacy table " + table + " must be gone").isFalse();
            }

            // Mọi neo booking phải đã bị gỡ khỏi các bảng ĐƯỢC GIỮ LẠI.
            for (String table : List.of("payment_orders", "wallet_transactions", "loyalty_transactions",
                    "refund_requests", "disputes", "reviews")) {
                assertThat(columnType(c, table, "booking_id"))
                        .as(table + ".booking_id must be dropped").isNull();
            }
            for (String column : List.of("gym_service_id", "training_package_id")) {
                assertThat(columnType(c, "reviews", column))
                        .as("reviews." + column + " must be dropped").isNull();
                assertThat(columnType(c, "pt_assignments", column))
                        .as("pt_assignments." + column + " must be dropped (câu 24)").isNull();
            }
            // Câu 24: chi nhánh là đích DUY NHẤT còn lại của phân công PT.
            assertThat(isNullable(c, "pt_assignments", "gym_branch_id"))
                    .as("pt_assignments.gym_branch_id must be NOT NULL").isFalse();
            // V77 siết NOT NULL sau khi V76 backfill.
            assertThat(isNullable(c, "reviews", "target_type"))
                    .as("reviews.target_type must be NOT NULL").isFalse();

            // V80: escrow đã reset, sổ cái ví có bút toán số dư đầu kỳ.
            assertThat(columnType(c, "wallet_transactions", "type"))
                    .as("wallet_transactions.type must accept ADJUSTMENT").contains("'ADJUSTMENT'");
        }
    }

    /**
     * V81 phải chạy lại được sau khi CHẾT GIỮA CHỪNG.
     *
     * <p>Sự cố thật (2026-08-14): V81 drop {@code customer_packages} trước
     * {@code bookings} trong khi hai bảng tham chiếu VÒNG TRÒN nhau, nên chết ở
     * giữa. MariaDB không có DDL trong transaction ⇒ Flyway không rollback được:
     * phần đầu đã áp dụng xong, phần sau chưa, và {@code flyway_schema_history}
     * giữ một dòng failed khiến app không boot nổi.
     *
     * <p>Sửa lỗi thứ tự thôi thì CHƯA đủ — câu hỏi còn lại là "chạy lại có được
     * không". Nếu bất kỳ câu nào trong V81 không idempotent thì lần chạy thứ hai
     * chết ngay ở câu đầu tiên đã áp dụng xong, và schema kẹt vĩnh viễn: cách
     * duy nhất còn lại là drop cả database.
     *
     * <p>Cách kiểm: migrate tới V80, chạy TAY toàn bộ script V81, rồi để Flyway
     * chạy V81 lần nữa. Mọi câu lúc này đều gặp đối tượng đã biến mất. Qua được
     * nghĩa là mọi câu đều idempotent — tính chất này mạnh hơn "khôi phục được
     * từ đúng điểm chết hôm qua", vì nó đúng với MỌI điểm chết.
     */
    @Test
    void v81_everyStatementIsIdempotent_soAFailedRunCanBeResumed() throws Exception {
        String schema = "fitmatch_v81_resume_it";
        // Schema riêng: test này cố ý để lại trạng thái bất thường giữa chừng,
        // không được để dính sang test schema chính.
        try (Connection c = DriverManager.getConnection(server, user, password);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + schema);
            s.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String url = (server.endsWith("/") ? server : server + "/") + schema;

        try {
            Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("80"))
                    .load()
                    .migrate();

            // Mô phỏng "lần chạy trước đã áp dụng xong" — chạy tay, ngoài Flyway,
            // nên schema_history vẫn dừng ở V80 y như khi migration thất bại.
            try (Connection c = DriverManager.getConnection(url, user, password);
                 Statement s = c.createStatement()) {
                for (String stmt : readStatements("/db/migration/V81__drop_legacy_booking_tables.sql")) {
                    s.execute(stmt);
                }
            }

            var result = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(result.success)
                    .as("V81 phải chạy lại được trên schema đã áp dụng dở").isTrue();
            assertThat(result.migrationsExecuted).as("đúng một migration còn lại").isEqualTo(1);

            try (Connection c = DriverManager.getConnection(url, user, password)) {
                for (String table : List.of("bookings", "customer_packages", "gym_services",
                        "training_packages", "blocked_times", "availability_slots")) {
                    assertThat(tableExists(c, schema, table))
                            .as("chạy lại vẫn phải dọn sạch " + table).isFalse();
                }
            }
        } finally {
            try (Connection c = DriverManager.getConnection(server, user, password);
                 Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    /**
     * Tách file .sql thành từng câu lệnh. V81 chỉ có DDL thuần, không dấu chấm
     * phẩy trong chuỗi và không đổi DELIMITER, nên bỏ comment rồi cắt theo ';'
     * là đủ — không cần parser thật.
     */
    private static List<String> readStatements(String resource) throws Exception {
        String sql;
        try (var in = FlywayMigrationTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("không đọc được " + resource).isNotNull();
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String withoutComments = sql.lines()
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .collect(Collectors.joining("\n"));

        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean tableExists(Connection c, String schema, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select count(*) from information_schema.TABLES "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select count(*) from information_schema.TABLES "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static int countWhere(Connection c, String table, String predicate) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from " + table + " where " + predicate)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean hasIndex(Connection c, String table, String indexName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select count(*) from information_schema.STATISTICS "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ? and INDEX_NAME = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            ps.setString(3, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean isNullable(Connection c, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select IS_NULLABLE from information_schema.COLUMNS "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ? and COLUMN_NAME = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "YES".equals(rs.getString(1));
            }
        }
    }

    private static int rowCount(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean hasUniqueIndex(Connection c, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select NON_UNIQUE from information_schema.STATISTICS "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ? and COLUMN_NAME = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt(1) == 0) return true;
                }
                return false;
            }
        }
    }

    private static String columnType(Connection c, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select COLUMN_TYPE from information_schema.COLUMNS "
                        + "where TABLE_SCHEMA = ? and TABLE_NAME = ? and COLUMN_NAME = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        return columnType(c, table, column) != null;
    }
}
