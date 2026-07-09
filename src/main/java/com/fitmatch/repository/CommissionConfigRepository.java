package com.fitmatch.repository;

import com.fitmatch.entity.CommissionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommissionConfigRepository extends JpaRepository<CommissionConfig, Long> {

    /** Bản cấu hình mới nhất đang hiệu lực. */
    Optional<CommissionConfig> findTopByOrderByIdDesc();
}
