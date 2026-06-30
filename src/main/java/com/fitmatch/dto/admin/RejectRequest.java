package com.fitmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lý do từ chối dùng chung cho duyệt PT/Gym verification, refund, withdrawal...
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 1000)
    private String reason;
}
