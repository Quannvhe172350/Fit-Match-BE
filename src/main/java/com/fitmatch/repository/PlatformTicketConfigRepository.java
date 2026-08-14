package com.fitmatch.repository;

import com.fitmatch.entity.PlatformTicketConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformTicketConfigRepository extends JpaRepository<PlatformTicketConfig, Long> {

    /** Mẫu "một dòng hiệu lực": luôn đọc bản mới nhất (V67 seed sẵn một dòng). */
    Optional<PlatformTicketConfig> findTopByOrderByIdDesc();
}
