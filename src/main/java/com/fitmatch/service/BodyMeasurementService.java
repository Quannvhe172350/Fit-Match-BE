package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.measurement.BodyMeasurementRequest;
import com.fitmatch.dto.measurement.BodyMeasurementResponse;
import org.springframework.data.domain.Pageable;

/** UC-051: khách hàng tự theo dõi số đo cơ thể / tiến trình tập luyện. */
public interface BodyMeasurementService {

    PageResponse<BodyMeasurementResponse> list(String username, Pageable pageable);

    BodyMeasurementResponse create(String username, BodyMeasurementRequest request);

    BodyMeasurementResponse update(String username, Long id, BodyMeasurementRequest request);

    void delete(String username, Long id);
}
