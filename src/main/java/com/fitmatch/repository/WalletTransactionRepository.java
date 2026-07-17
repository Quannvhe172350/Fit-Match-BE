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

    /** UC-076: tổng số tiền theo loại bút toán trong khoảng; gymId null = toàn nền tảng. */
    @org.springframework.data.jpa.repository.Query(
            "select t.type, coalesce(sum(t.amount), 0) from WalletTransaction t "
            + "where t.createdAt >= :from and t.createdAt < :to "
            + "and (:gymId is null or t.wallet.gymProfile.id = :gymId) group by t.type")
    List<Object[]> sumByTypeInRange(
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("gymId") Long gymId);
}
