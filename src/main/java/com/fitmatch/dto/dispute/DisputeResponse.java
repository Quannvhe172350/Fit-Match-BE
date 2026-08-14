package com.fitmatch.dto.dispute;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.entity.Dispute;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Hồ sơ tranh chấp (UC-063..068). */
@Getter
@Builder
public class DisputeResponse {

    private Long id;
    /** Mô hình vé: luôn có ticketId; sessionId null = tranh chấp CẤP VÉ (câu 34). */
    private Long ticketId;
    private Long sessionId;
    private java.time.LocalDate sessionDate;
    private String ticketName;
    private String customerName;
    private Long gymId;
    private String gymName;
    private Long ptProfileId;
    private String ptName;
    private String openedByRole;
    private String openedBy;
    private String reason;
    private DisputeStatus status;
    private DisputeResolution resolution;
    private BigDecimal refundAmount;
    private BigDecimal frozenAmount;
    private String moderatorNote;
    /** D-12: moderator đang phụ trách (claim khi startReview). */
    private String assignedModerator;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static DisputeResponse of(Dispute d) {
        var t = d.getTicket();
        var session = d.getSession();
        return DisputeResponse.builder()
                .id(d.getId())
                .ticketId(t.getId())
                .ticketName(t.getTicketType().getName())
                .sessionId(session != null ? session.getId() : null)
                .sessionDate(session != null ? session.getSessionDate() : null)
                .customerName(t.getCustomer().getUsername())
                .gymId(t.getGymProfile().getId())
                .gymName(t.getGymProfile().getGymName())
                // PT chỉ có nghĩa với tranh chấp cấp buổi — tranh chấp cấp vé có
                // thể trải nhiều PT khác nhau nên không quy về một người được.
                .ptProfileId(session != null && session.getPtProfile() != null
                        ? session.getPtProfile().getId() : null)
                .ptName(session != null && session.getPtProfile() != null
                        ? session.getPtProfile().getDisplayName() : null)
                .openedByRole(d.getOpenedByRole())
                .openedBy(d.getCreatedBy())
                .reason(d.getReason())
                .status(d.getStatus())
                .resolution(d.getResolution())
                .refundAmount(d.getRefundAmount())
                .frozenAmount(d.getFrozenAmount())
                .moderatorNote(d.getModeratorNote())
                .assignedModerator(d.getAssignedModerator())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
