package com.fitmatch.dto.gym;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Upsert chính sách vận hành của Gym (UC-017).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPolicyRequest {

    @Size(max = 2000)
    private String bookingPolicy;

    @Size(max = 2000)
    private String cancellationPolicy;

    @Size(max = 2000)
    private String noShowPolicy;

    @Size(max = 2000)
    private String houseRules;
}
