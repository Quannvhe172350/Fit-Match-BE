package com.fitmatch.repository;

import com.fitmatch.common.enums.TokenType;
import com.fitmatch.entity.User;
import com.fitmatch.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    Optional<VerificationToken> findByTokenAndType(String token, TokenType type);

    /** Vô hiệu hoá các token cũ cùng loại của user trước khi phát hành token mới. */
    @Modifying
    @Query("UPDATE VerificationToken t SET t.used = true WHERE t.user = :user AND t.type = :type AND t.used = false")
    void invalidateExisting(User user, TokenType type);
}
