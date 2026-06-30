package com.fitmatch.service;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtProfileResponse;
import org.springframework.data.domain.Pageable;

public interface AdminPtVerificationService {

    /** UC-29: liệt kê yêu cầu xác minh PT theo trạng thái (mặc định PENDING). */
    PageResponse<PtProfileResponse> list(VerificationStatus status, Pageable pageable);

    /** UC-29: xem chi tiết một yêu cầu xác minh PT (kèm tài liệu). */
    PtProfileResponse detail(Long profileId);

    /** UC-30: duyệt — chỉ khi PENDING -> APPROVED + active + nâng role ROLE_PT. */
    PtProfileResponse approve(Long profileId, String actorUsername);

    /** UC-30: từ chối — chỉ khi PENDING -> REJECTED + lý do. */
    PtProfileResponse reject(Long profileId, String reason, String actorUsername);
}
