package com.fitmatch;

import com.fitmatch.common.enums.WalletTxnType;
import org.flywaydb.core.Flyway;
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
import java.util.List;

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
                .as("latest applied migration version").isEqualTo("64");

        try (Connection c = DriverManager.getConnection(schemaUrl(), user, password)) {
            // --- P0-3 guard: MỌI giá trị WalletTxnType phải có trong cột ENUM ---
            // (bắt được cả trường hợp thêm enum Java mới mà quên migration về sau).
            String walletType = columnType(c, "wallet_transactions", "type");
            for (WalletTxnType t : WalletTxnType.values()) {
                assertThat(walletType)
                        .as("wallet_transactions.type ENUM must contain " + t.name())
                        .contains("'" + t.name() + "'");
            }

            // --- V35: cờ hoàn điểm/voucher ---
            assertThat(hasColumn(c, "bookings", "promo_released"))
                    .as("bookings.promo_released (V35)").isTrue();

            // --- V32/V33/V36: cột @Version optimistic lock ---
            for (String table : List.of("bookings", "disputes", "customer_packages",
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
