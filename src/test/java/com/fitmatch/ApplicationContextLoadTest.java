package com.fitmatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Nạp TOÀN BỘ Spring context trên MariaDB thật. Đây là cổng nghiệm thu duy nhất
 * bắt được ba lớp lỗi mà 59 file unit test mock-based không thể thấy:
 *
 * <ol>
 *   <li>{@code ddl-auto: validate} — entity map vào cột đã bị migration drop
 *       (vd {@code loyalty_transactions.booking_id} sau V77).</li>
 *   <li>Derived query của Spring Data trỏ vào property không còn tồn tại. Tên
 *       method là CHUỖI, được phân giải lúc tạo bean chứ không phải lúc biên
 *       dịch — nên {@code existsByPtProfile_IdAndGymService_Id} vẫn compile sạch
 *       sau khi trường {@code gymService} biến mất, rồi mới nổ lúc khởi động.</li>
 *   <li>Bean wiring: constructor injection thiếu bean, cấu hình sai.</li>
 * </ol>
 *
 * <p>Bài học P4: "mvn test xanh" KHÔNG đồng nghĩa với "app chạy được". Trước khi
 * có test này, cả hai lớp lỗi trên chỉ lộ ra khi người dùng tự bấm Run.
 *
 * <p>Guard bằng {@code IT_DB_URL} nên {@code mvn test} không có DB sẽ BỎ QUA.
 * Chạy thật:
 * <pre>
 *   IT_DB_URL=jdbc:mariadb://localhost:3306/ IT_DB_USER=root IT_DB_PASSWORD=xxx \
 *       mvnw test -Dtest=ApplicationContextLoadTest
 * </pre>
 * Dùng schema riêng {@code fitmatch_context_it}, tạo lúc chạy và drop sau — không
 * đụng DB dev.
 */
/*
 * webEnvironment mặc định (MOCK): KHÔNG mở cổng, nhưng vẫn là ứng dụng servlet
 * nên security auto-config được nạp. Đặt NONE sẽ làm
 * AuthenticationConfiguration biến mất và test đỏ vì lý do của chính nó.
 */
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
@SpringBootTest
class ApplicationContextLoadTest {

    private static final String SCHEMA = "fitmatch_context_it";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String server = System.getenv("IT_DB_URL");
        String user = System.getenv().getOrDefault("IT_DB_USER", "root");
        String password = System.getenv().getOrDefault("IT_DB_PASSWORD", "");

        registry.add("spring.datasource.url",
                () -> server + SCHEMA + "?createDatabaseIfNotExist=true");
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);

        // Ba property này không có default trong application.yml (fail-fast ở
        // môi trường thật) nên phải cấp giá trị giả để context nạp được.
        registry.add("jwt.secret", () -> "dGVzdC1vbmx5LXNlY3JldC1mb3ItY29udGV4dC1sb2FkLXRlc3QtMzJi");
        registry.add("app.vietqr.bank-bin", () -> "970415");
        registry.add("app.vietqr.account-no", () -> "0000000000");
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        String server = System.getenv("IT_DB_URL");
        if (server == null) {
            return;
        }
        try (Connection c = DriverManager.getConnection(server,
                System.getenv().getOrDefault("IT_DB_USER", "root"),
                System.getenv().getOrDefault("IT_DB_PASSWORD", ""));
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    /**
     * Không có assertion: context nạp được đã là điều cần chứng minh. Flyway
     * dựng schema từ V1, Hibernate validate toàn bộ entity, Spring Data phân
     * giải mọi derived query — bất kỳ lệch nào đều làm test đỏ.
     */
    @Test
    void contextLoads() {
    }
}
