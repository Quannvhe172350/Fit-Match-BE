package com.fitmatch.dto.user;

import com.fitmatch.common.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private String fullName;

    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;

    private Gender gender;

    private String location;

    private String avatarUrl;

    @Positive(message = "Height must be positive")
    private Double height;

    @Positive(message = "Weight must be positive")
    private Double weight;

    private String mainGoal;

    @Valid
    private EmergencyContactDto emergencyContact;

    @Valid
    private FitnessPreferencesDto fitnessPreferences;
}
