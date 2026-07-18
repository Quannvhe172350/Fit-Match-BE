package com.fitmatch.dto.gym;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitGymRegistrationRequest {

    @NotBlank(message = "Gym name is required")
    @Size(max = 150)
    private String gymName;

    @Size(max = 2000)
    private String description;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 30)
    private String phone;

    @NotEmpty(message = "At least one verification document is required")
    @Valid
    private List<GymDocumentDto> documents;
}
