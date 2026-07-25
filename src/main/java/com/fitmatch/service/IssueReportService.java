package com.fitmatch.service;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.report.IssueReportRequest;
import com.fitmatch.dto.report.IssueReportResponse;
import org.springframework.data.domain.Pageable;

/** UC-070: báo cáo vấn đề dịch vụ/hành vi (Gym/PT/Booking) + xử lý kiểm duyệt. */
public interface IssueReportService {

    IssueReportResponse create(String reporterUsername, IssueReportRequest request);

    PageResponse<IssueReportResponse> my(String reporterUsername, Pageable pageable);

    PageResponse<IssueReportResponse> queue(ReportStatus status, Pageable pageable);

    IssueReportResponse resolve(String moderatorUsername, Long id, String note);

    IssueReportResponse dismiss(String moderatorUsername, Long id, String note);
}
