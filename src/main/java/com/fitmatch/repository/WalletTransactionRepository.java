package com.fitmatch.repository;

import com.fitmatch.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByWallet_IdOrderByIdDesc(Long walletId, Pageable pageable);

    List<WalletTransaction> findByBookingIdOrderByIdAsc(Long bookingId);
}
