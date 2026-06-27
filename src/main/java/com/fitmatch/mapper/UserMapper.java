package com.fitmatch.mapper;

import com.fitmatch.dto.user.EmergencyContactDto;
import com.fitmatch.dto.user.FitnessPreferencesDto;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;

import java.util.Arrays;
import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        EmergencyContactDto emergencyContact = null;
        if (user.getEmergencyContactName() != null
                || user.getEmergencyContactRelationship() != null
                || user.getEmergencyContactPhone() != null) {
            emergencyContact = EmergencyContactDto.builder()
                    .name(user.getEmergencyContactName())
                    .relationship(user.getEmergencyContactRelationship())
                    .phone(user.getEmergencyContactPhone())
                    .build();
        }

        FitnessPreferencesDto fitnessPreferences = null;
        if (user.getFitnessStyles() != null
                || user.getFitnessFrequency() != null
                || user.getFitnessEquipmentAccess() != null
                || user.getFitnessInjuries() != null) {
            fitnessPreferences = FitnessPreferencesDto.builder()
                    .styles(splitStyles(user.getFitnessStyles()))
                    .frequency(user.getFitnessFrequency())
                    .equipmentAccess(user.getFitnessEquipmentAccess())
                    .injuries(user.getFitnessInjuries())
                    .build();
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .gender(user.getGender())
                .location(user.getLocation())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .height(user.getHeight())
                .weight(user.getWeight())
                .mainGoal(user.getMainGoal())
                .emergencyContact(emergencyContact)
                .fitnessPreferences(fitnessPreferences)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static List<String> splitStyles(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String joinStyles(List<String> styles) {
        if (styles == null || styles.isEmpty()) return null;
        return String.join(",", styles);
    }
}
