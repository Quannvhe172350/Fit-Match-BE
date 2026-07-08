package com.fitmatch.service;

import com.fitmatch.dto.booking.WaitlistRequest;
import com.fitmatch.dto.booking.WaitlistResponse;

import java.util.List;

/**
 * UC-044: danh sách chờ khi slot mong muốn không còn.
 */
public interface WaitlistService {

    WaitlistResponse join(String customerUsername, WaitlistRequest request);

    void leave(String customerUsername, Long entryId);

    List<WaitlistResponse> myEntries(String customerUsername);

    /** Gym xem danh sách chờ của một dịch vụ/gói của mình để chủ động liên hệ. */
    List<WaitlistResponse> listForGym(String gymUsername, Long serviceId, Long packageId);
}
