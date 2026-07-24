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
                .as("latest applied migration version").isEqualTo("45");

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
