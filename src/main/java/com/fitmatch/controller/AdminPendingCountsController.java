package com.fitmatch.controller;

import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.AdminPendingCountsResponse;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.IssueReportRepository;
import com.fitmatch.repository.PaymentTransactionRepository;
import com.fitmatch.repository.ReviewReportRepository;
import com.fitmatch.repository.WithdrawalRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V94: đếm việc tồn cho chấm đỏ trên menu khu quản trị.
 *
 * <p>Mở cho cả ba vai admin ({@code ADMIN}, {@code MODERATOR},
 * {@code FINANCE_ADMIN}) và trả VỀ TẤT CẢ các con số. Không lọc theo vai ở đây
 * vì sidebar FE đã lọc mục hiển thị theo đúng bảng quyền của nó — moderator
 * không thấy mục "Rút tiền" thì con số ấy cũng không đi tới đâu. Đây là số
 * lượng việc tồn ở mức tổng, không phải dữ liệu của ai, nên không có gì rò rỉ
 * khi một vai nhận thừa một con số mà họ không dùng tới.
 *
 * <p>Truy vấn thuần đếm có index (mọi bảng liên quan đều có index trên cột
 * status) nên endpoint này chịu được nhịp poll của FE.
 */
@RestController
@RequestMapping("/api/admin/pending-counts")
@RequiredArgsConstructor
@Tag(name = "A. Admin - Work Queues", description = "Số việc tồn của từng khu vực quản trị")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR','FINANCE_ADMIN')")
public class AdminPendingCountsController {

    /** Tranh chấp chưa xong: vừa mở lẫn đang xem xét đều là việc còn phải làm. */
    private static final List<DisputeStatus> OPEN_DISPUTES =
            List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);

    private final GymProfileRepository gymProfileRepository;
    private final DisputeRepository disputeRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final IssueReportRepository issueReportRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Operation(summary = "Số việc tồn của từng mục quản trị",
            description = "Actor: **Admin / Moderator / Finance Admin**. Nguồn của chấm đỏ cạnh "
                    + "mỗi mục menu. Tên trường khớp navKey của sidebar FE. Chỉ có mặt những khu "
                    + "vực thật sự có hàng đợi — mục không có việc chờ thì không xuất hiện ở đây.")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPendingCountsResponse>> pendingCounts() {
        return ResponseEntity.ok(ApiResponse.success(AdminPendingCountsResponse.builder()
                .verification(gymProfileRepository.countByVerificationStatus(VerificationStatus.PENDING))
                .disputes(disputeRepository.countByStatusIn(OPEN_DISPUTES))
                .reviews(reviewReportRepository.countByStatus(ReportStatus.OPEN))
                .issueReports(issueReportRepository.countByStatus(ReportStatus.OPEN))
                .withdrawals(withdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING))
                .reconciliation(paymentTransactionRepository.countByReconStatus(ReconStatus.NEEDS_REVIEW))
                .build()));
    }
}
