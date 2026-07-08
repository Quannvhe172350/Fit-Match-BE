package com.fitmatch.dto.booking;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Đăng ký danh sách chờ (UC-044): đúng MỘT trong serviceId/packageId.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistRequest {

    private Long serviceId;

    private Long packageId;

    private LocalDateTime preferredStart;

    @Size(max = 500)
    private String note;
}
