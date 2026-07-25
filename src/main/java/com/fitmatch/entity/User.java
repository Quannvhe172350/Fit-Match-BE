package com.fitmatch.entity;

import com.fitmatch.common.enums.Gender;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column
    private String location;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /**
     * Phiên bản token (UC-003/004): nhúng vào claim JWT; tăng lên khi logout /
     * đổi / reset mật khẩu để vô hiệu hoá toàn bộ access + refresh token cũ.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    /** P1-1.6: số lần đăng nhập sai liên tiếp; reset về 0 khi đăng nhập thành công. */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** P1-1.6: thời điểm hết khóa tạm sau khi vượt ngưỡng đăng nhập sai; null = không khóa. */
    @Column(name = "lockout_until")
    private java.time.LocalDateTime lockoutUntil;

    // Fitness Metrics
    @Column
    private Double height;

    @Column
    private Double weight;

    @Column(name = "main_goal")
    private String mainGoal;

    // Emergency Contact (flat columns)
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_relationship")
    private String emergencyContactRelationship;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    // Fitness Preferences (flat columns)
    @Column(name = "fitness_styles", columnDefinition = "TEXT")
    private String fitnessStyles;

    @Column(name = "fitness_frequency")
    private String fitnessFrequency;

    @Column(name = "fitness_equipment_access")
    private String fitnessEquipmentAccess;

    @Column(name = "fitness_injuries", columnDefinition = "TEXT")
    private String fitnessInjuries;
}
