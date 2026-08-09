package com.fitmatch.repository;

import com.fitmatch.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByGymProfile_Id(Long gymProfileId);

    Optional<Wallet> findByGymProfile_User_Username(String username);

    /** V61 — ví khách hàng theo user. */
    Optional<Wallet> findByUser_Id(Long userId);

    /** V61 — ví khách hàng theo tài khoản đăng nhập. */
    Optional<Wallet> findByUser_Username(String username);

    /** Khoá ví khi cập nhật số dư để tuần tự hoá bút toán đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> lockById(@Param("id") Long id);
}
