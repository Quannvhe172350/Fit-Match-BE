package com.fitmatch.repository;

import com.fitmatch.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByUser_Username(String username);

    /** Khoá tài khoản khi tích/tiêu điểm để tuần tự hoá thay đổi số dư. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.id = :id")
    Optional<LoyaltyAccount> lockById(@Param("id") Long id);
}
