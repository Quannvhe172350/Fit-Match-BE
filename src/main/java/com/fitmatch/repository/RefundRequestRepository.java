package com.fitmatch.repository;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.entity.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Page<RefundRequest> findByStatus(RefundStatus status, Pageable pageable);
}
