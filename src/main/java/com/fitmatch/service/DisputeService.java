package com.fitmatch.service;

import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeEvidenceRequest;
import com.fitmatch.dto.dispute.DisputeEvidenceResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Tranh chấp/khiếu nại (UC-063..068). Bên liên quan (customer/gym/pt) mở và gửi
 * bằng chứng; moderator/admin xem, quyết định và áp dụng tài chính, đóng/chuyển cấp.
 */
public interface DisputeService {

    // ----- Các bên liên quan (UC-063/064) -----
    // Mở tranh chấp: POST /api/tickets/{id}/disputes (TicketDisputeService) —
    // cần biết vé/buổi và mức đóng băng theo cấp (câu 34).


    DisputeEvidenceResponse addEvidence(String username, Long disputeId, DisputeEvidenceRequest request);

    List<DisputeEvidenceResponse> evidence(String username, Long disputeId);

    DisputeResponse detail(String username, Long disputeId);

    PageResponse<DisputeResponse> myDisputes(String username, Pageable pageable);

    // ----- Moderator/Admin (UC-065..068) -----
    PageResponse<DisputeResponse> queue(DisputeStatus status, Pageable pageable);

    DisputeResponse startReview(String moderatorUsername, Long disputeId);

    DisputeResponse resolve(String moderatorUsername, Long disputeId, ResolveDisputeRequest request);

    DisputeResponse close(String moderatorUsername, Long disputeId, String note);

    DisputeResponse escalate(String moderatorUsername, Long disputeId, String note);

    List<DisputeEvidenceResponse> evidenceForModerator(Long disputeId);

    DisputeResponse detailForModerator(Long disputeId);
}
