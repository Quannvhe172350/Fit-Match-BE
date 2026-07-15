package com.fitmatch.service;

import com.fitmatch.dto.report.OperationalReportResponse;

import java.time.LocalDate;

/**
 * Báo cáo vận hành & tài chính (UC-076). Tổng hợp từ booking, sổ cái ví và
 * tranh chấp theo khoảng thời gian; phân quyền theo vai trò.
 */
public interface ReportService {

    /** Báo cáo toàn nền tảng (Admin/Finance). */
    OperationalReportResponse platformReport(LocalDate from, LocalDate to);

    /** Báo cáo của Gym đang đăng nhập (Gym Operator). */
    OperationalReportResponse gymReport(String gymUsername, LocalDate from, LocalDate to);
}
