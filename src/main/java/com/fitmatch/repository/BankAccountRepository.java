package com.fitmatch.repository;

import com.fitmatch.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByUser_UsernameOrderByDefaultAccountDescIdAsc(String username);

    Optional<BankAccount> findByIdAndUser_Username(Long id, String username);

    boolean existsByUser_UsernameAndBank_IdAndAccountNumber(String username, Long bankId, String accountNumber);

    long countByUser_Username(String username);

    /**
     * Bỏ cờ mặc định của mọi tài khoản khác trước khi set cái mới — mỗi user chỉ
     * được đúng một tài khoản mặc định. Bulk update nên phải flush/clear
     * persistence context ở tầng service trước khi đọc lại.
     */
    @Modifying
    @Query("update BankAccount b set b.defaultAccount = false "
            + "where b.user.username = :username and b.id <> :keepId and b.defaultAccount = true")
    int clearDefaultExcept(@Param("username") String username, @Param("keepId") Long keepId);
}
