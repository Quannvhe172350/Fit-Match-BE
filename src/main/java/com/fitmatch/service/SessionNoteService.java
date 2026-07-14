package com.fitmatch.service;

import com.fitmatch.dto.booking.SessionNoteRequest;
import com.fitmatch.dto.booking.SessionNoteResponse;

import java.util.List;

/**
 * Ghi chú/bằng chứng buổi tập (UC-048). Ghi: Gym sở hữu booking hoặc PT được
 * gán. Đọc: customer chủ booking, Gym sở hữu, PT được gán.
 */
public interface SessionNoteService {

    SessionNoteResponse addForGym(String gymUsername, Long bookingId, SessionNoteRequest request);

    SessionNoteResponse addForPt(String ptUsername, Long bookingId, SessionNoteRequest request);

    List<SessionNoteResponse> listForGym(String gymUsername, Long bookingId);

    List<SessionNoteResponse> listForPt(String ptUsername, Long bookingId);

    List<SessionNoteResponse> listForCustomer(String customerUsername, Long bookingId);
}
