package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.IssueTargetType;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.report.IssueReportRequest;
import com.fitmatch.dto.report.IssueReportResponse;
import com.fitmatch.entity.IssueReport;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.IssueReportRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.IssueReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueReportServiceImpl implements IssueReportService {

    private final IssueReportRepository issueReportRepository;
    private final GymProfileRepository gymProfileRepository;
    private final PtProfileRepository ptProfileRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public IssueReportResponse create(String reporterUsername, IssueReportRequest request) {
        String targetName = validateTargetAndResolveName(
                reporterUsername, request.getTargetType(), request.getTargetId());

        // Chống spam: một người chỉ mở một report đang xử lý cho mỗi đối tượng (cùng quy tắc ReviewReport).
        if (issueReportRepository.existsByCreatedByAndTargetTypeAndTargetIdAndStatus(
                reporterUsername, request.getTargetType(), request.getTargetId(), ReportStatus.OPEN)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "You already have an open report for this target");
        }

        IssueReport report = issueReportRepository.save(IssueReport.builder()
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .build());
        auditService.record(AuditActions.ISSUE_REPORT_OPEN, "IssueReport",
                String.valueOf(report.getId()),
                request.getTargetType() + " #" + request.getTargetId() + " reported by " + reporterUsername);
        log.info("Issue report {} opened by {} for {} #{}", report.getId(), reporterUsername,
                request.getTargetType(), request.getTargetId());
        return IssueReportResponse.of(report, targetName);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IssueReportResponse> my(String reporterUsername, Pageable pageable) {
        return PageResponse.of(issueReportRepository.findByCreatedBy(reporterUsername, pageable),
                r -> IssueReportResponse.of(r, resolveNameQuietly(r)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IssueReportResponse> queue(ReportStatus status, Pageable pageable) {
        var page = status == null
                ? issueReportRepository.findAll(pageable)
                : issueReportRepository.findByStatus(status, pageable);
        return PageResponse.of(page, r -> IssueReportResponse.of(r, resolveNameQuietly(r)));
    }

    @Override
    @Transactional
    public IssueReportResponse resolve(String moderatorUsername, Long id, String note) {
        return decide(moderatorUsername, id, note, ReportStatus.RESOLVED,
                AuditActions.ISSUE_REPORT_RESOLVE);
    }

    @Override
    @Transactional
    public IssueReportResponse dismiss(String moderatorUsername, Long id, String note) {
        return decide(moderatorUsername, id, note, ReportStatus.DISMISSED,
                AuditActions.ISSUE_REPORT_DISMISS);
    }

    private IssueReportResponse decide(String moderatorUsername, Long id, String note,
                                       ReportStatus target, String auditAction) {
        IssueReport report = issueReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue report", id));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only OPEN reports can be processed");
        }
        report.setStatus(target);
        report.setModeratorNote(note);
        report = issueReportRepository.save(report);
        auditService.record(auditAction, "IssueReport", String.valueOf(id),
                target + " by " + moderatorUsername);
        log.info("Issue report {} {} by {}", id, target, moderatorUsername);
        return IssueReportResponse.of(report, resolveNameQuietly(report));
    }

    /**
     * Validate đối tượng tồn tại; với BOOKING chỉ các bên liên quan mới được báo cáo
     * (chống dò ID booking của người khác — trả 404 thay 403).
     */
    private String validateTargetAndResolveName(String reporter, IssueTargetType type, Long targetId) {
        switch (type) {
            case GYM -> {
                return gymProfileRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Gym profile", targetId))
                        .getGymName();
            }
            case PT -> {
                return ptProfileRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("PT profile", targetId))
                        .getDisplayName();
            }
            case BOOKING -> {
                // Enum giữ tên cũ để dữ liệu báo cáo trước đây vẫn đọc được;
                // đối tượng bây giờ là VÉ.
                com.fitmatch.entity.Ticket ticket = ticketRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket", targetId));
                if (!isParty(ticket, reporter)) {
                    throw new ResourceNotFoundException("Ticket", targetId);
                }
                return "Vé #" + targetId;
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported target type");
        }
    }

    private boolean isParty(com.fitmatch.entity.Ticket t, String username) {
        if (t.getCustomer() != null && username.equals(t.getCustomer().getUsername())) return true;
        return t.getGymProfile() != null && t.getGymProfile().getUser() != null
                && username.equals(t.getGymProfile().getUser().getUsername());
    }

    /** Tên hiển thị cho list — nuốt lỗi (đối tượng có thể đã bị xóa/ẩn sau khi report). */
    private String resolveNameQuietly(IssueReport r) {
        try {
            return switch (r.getTargetType()) {
                case GYM -> gymProfileRepository.findById(r.getTargetId())
                        .map(g -> g.getGymName()).orElse(null);
                case PT -> ptProfileRepository.findById(r.getTargetId())
                        .map(p -> p.getDisplayName()).orElse(null);
                case BOOKING -> "Vé #" + r.getTargetId();
            };
        } catch (Exception e) {
            return null;
        }
    }
}
