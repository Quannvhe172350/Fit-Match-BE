package com.fitmatch.repository;

import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    Page<WithdrawalRequest> findByWallet_GymProfile_User_Username(String username, Pageable pageable);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);
}
