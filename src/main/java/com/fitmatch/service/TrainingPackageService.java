package com.fitmatch.service;

import com.fitmatch.dto.gym.TrainingPackageRequest;
import com.fitmatch.dto.gym.TrainingPackageResponse;

import java.util.List;

/**
 * UC-025: Gym tạo và duy trì gói tập (số buổi, hạn dùng, giá, điều kiện sử dụng).
 */
public interface TrainingPackageService {

    TrainingPackageResponse create(String username, TrainingPackageRequest request);

    TrainingPackageResponse update(String username, Long id, TrainingPackageRequest request);

    void deactivate(String username, Long id);

    List<TrainingPackageResponse> list(String username);

    TrainingPackageResponse detail(String username, Long id);
}
