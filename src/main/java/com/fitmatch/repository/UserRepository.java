package com.fitmatch.repository;

import com.fitmatch.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dùng cho kiểm tra trùng email khi cập nhật hồ sơ (UC-05), loại trừ chính user hiện tại. */
    boolean existsByEmailAndIdNot(String email, Long id);
}
