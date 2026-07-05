package com.fitmatch.service;

import com.fitmatch.dto.gym.GymMediaRequest;
import com.fitmatch.dto.gym.GymMediaResponse;

import java.util.List;

/**
 * UC-016: Gym quản lý ảnh/media công khai của Gym và chi nhánh.
 */
public interface GymMediaService {

    GymMediaResponse add(String username, GymMediaRequest request);

    void delete(String username, Long mediaId);

    List<GymMediaResponse> list(String username, Long branchId);
}
