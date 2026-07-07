package com.fitmatch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả kiểm tra khả dụng (UC-030): available = true khi không có lý do vi phạm.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityCheckResponse {

    private boolean available;
    private List<String> reasons;

    public static AvailabilityCheckResponse of(List<String> reasons) {
        return AvailabilityCheckResponse.builder()
                .available(reasons.isEmpty())
                .reasons(reasons)
                .build();
    }
}
