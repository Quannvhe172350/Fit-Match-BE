package com.fitmatch.repository;

import com.fitmatch.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /** Idempotency: giao dịch Casso đã xử lý chưa. */
    boolean existsByExternalId(String externalId);
}
