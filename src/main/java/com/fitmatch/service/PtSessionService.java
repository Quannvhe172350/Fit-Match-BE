package com.fitmatch.service;

import com.fitmatch.dto.ticket.TrainingSessionResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Lịch dạy của PT. Read-only: PT không nhận/từ chối buổi nào cả — khách chọn
 * PT từ khung giờ PT đã tự khai, nên việc đồng ý đã nằm ở bước khai lịch.
 */
public interface PtSessionService {

    List<TrainingSessionResponse> mySessions(String ptUsername, LocalDate from, LocalDate to);
}
