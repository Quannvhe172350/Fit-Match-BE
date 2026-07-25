package com.fitmatch.repository;

import com.fitmatch.entity.BodyMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {

    Page<BodyMeasurement> findByUser_Username(String username, Pageable pageable);

    /** Ownership check gộp trong query — không leak bản ghi của người khác (404 thay 403). */
    Optional<BodyMeasurement> findByIdAndUser_Username(Long id, String username);
}
