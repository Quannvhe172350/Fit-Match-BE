package com.fitmatch.service.impl;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.measurement.BodyMeasurementRequest;
import com.fitmatch.dto.measurement.BodyMeasurementResponse;
import com.fitmatch.entity.BodyMeasurement;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BodyMeasurementRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.BodyMeasurementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BodyMeasurementServiceImpl implements BodyMeasurementService {

    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BodyMeasurementResponse> list(String username, Pageable pageable) {
        return PageResponse.of(
                bodyMeasurementRepository.findByUser_Username(username, pageable),
                BodyMeasurementResponse::of);
    }

    @Override
    @Transactional
    public BodyMeasurementResponse create(String username, BodyMeasurementRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        BodyMeasurement m = BodyMeasurement.builder().user(user).build();
        apply(m, request);
        m = bodyMeasurementRepository.save(m);
        log.info("Body measurement {} created by {}", m.getId(), username);
        return BodyMeasurementResponse.of(m);
    }

    @Override
    @Transactional
    public BodyMeasurementResponse update(String username, Long id, BodyMeasurementRequest request) {
        BodyMeasurement m = requireOwned(username, id);
        apply(m, request);
        return BodyMeasurementResponse.of(bodyMeasurementRepository.save(m));
    }

    @Override
    @Transactional
    public void delete(String username, Long id) {
        bodyMeasurementRepository.delete(requireOwned(username, id));
        log.info("Body measurement {} deleted by {}", id, username);
    }

    /** Ownership gộp trong query — người khác nhận 404 (không leak tồn tại bản ghi). */
    private BodyMeasurement requireOwned(String username, Long id) {
        return bodyMeasurementRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Body measurement", id));
    }

    private void apply(BodyMeasurement m, BodyMeasurementRequest r) {
        m.setMeasuredAt(r.getMeasuredAt());
        m.setWeightKg(r.getWeightKg());
        m.setHeightCm(r.getHeightCm());
        m.setBodyFatPercent(r.getBodyFatPercent());
        m.setChestCm(r.getChestCm());
        m.setWaistCm(r.getWaistCm());
        m.setHipCm(r.getHipCm());
        m.setNote(r.getNote());
    }
}
