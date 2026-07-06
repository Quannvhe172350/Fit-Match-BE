package com.fitmatch.dto.pt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gán PT vào đúng MỘT đích: chi nhánh, dịch vụ hoặc gói tập (UC-022).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtAssignmentRequest {

    private Long branchId;

    private Long serviceId;

    private Long packageId;
}
