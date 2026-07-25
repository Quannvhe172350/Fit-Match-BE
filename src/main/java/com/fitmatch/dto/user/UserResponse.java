package com.fitmatch.dto.user;

import com.fitmatch.common.enums.Gender;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Gender gender;
    private String location;
    private String avatarUrl;
    private Role role;
    private UserStatus status;
    private boolean emailVerified;

    // Fitness Metrics
    private Double height;
    private Double weight;
    private String mainGoal;

    // Nested objects
    private EmergencyContactDto emergencyContact;
    private FitnessPreferencesDto fitnessPreferences;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
