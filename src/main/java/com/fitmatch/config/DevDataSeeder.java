package com.fitmatch.config;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.entity.User;
import com.fitmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seed dữ liệu nền tảng khi khởi động — CHỈ ở profile {@code local}/{@code dev}
 * (không bao giờ chạy ở prod). Tạo sẵn mỗi vai trò một tài khoản đã xác minh
 * email để có thể chạy toàn bộ nghiệp vụ qua API thật: đăng ký/duyệt gym, tạo
 * catalog, đặt lịch, thanh toán, hoàn tất, mở & xử lý tranh chấp (UC-063..067),
 * hoàn tiền, rút tiền. Idempotent: bỏ qua tài khoản đã tồn tại nên chạy lại
 * nhiều lần vô hại. Commission config đã được seed sẵn ở V18.
 *
 * <p>Mật khẩu mặc định dùng chung cho các tài khoản seed: {@value #DEFAULT_PASSWORD}.
 */
@Slf4j
@Component
// P2: chỉ seed ở 'local'. Trước đây bật cả 'dev' -> nếu 'dev' là staging truy
// cập được từ ngoài thì tồn tại tài khoản ADMIN với mật khẩu cứng (ai cũng biết).
@Profile("local")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    static final String DEFAULT_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Tài khoản seed: username, email, họ tên, vai trò. */
    private record SeedAccount(String username, String email, String fullName, Role role) {
    }

    private static final List<SeedAccount> ACCOUNTS = List.of(
            new SeedAccount("admin", "admin@fitmatch.local", "Platform Admin", Role.ROLE_ADMIN),
            new SeedAccount("moderator", "moderator@fitmatch.local", "Dispute Moderator", Role.ROLE_MODERATOR),
            new SeedAccount("finance", "finance@fitmatch.local", "Finance Admin", Role.ROLE_FINANCE_ADMIN),
            new SeedAccount("operator", "operator@fitmatch.local", "Gym Operator", Role.ROLE_GYM_OPERATOR),
            new SeedAccount("customer", "customer@fitmatch.local", "Test Customer", Role.ROLE_CUSTOMER)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (SeedAccount acc : ACCOUNTS) {
            if (userRepository.existsByUsername(acc.username()) || userRepository.existsByEmail(acc.email())) {
                continue;
            }
            userRepository.save(User.builder()
                    .username(acc.username())
                    .email(acc.email())
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .fullName(acc.fullName())
                    .role(acc.role())
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build());
            created++;
            log.info("[seed] created {} account: {}", acc.role(), acc.username());
        }
        if (created > 0) {
            log.warn("[seed] {} dev account(s) created. Shared password: '{}'. "
                    + "DevDataSeeder chỉ chạy ở profile local/dev.", created, DEFAULT_PASSWORD);
        } else {
            log.info("[seed] dev accounts already present, nothing to do");
        }
    }
}
