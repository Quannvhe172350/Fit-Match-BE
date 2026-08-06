package com.fitmatch.repository;

import com.fitmatch.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /** UC-003: tra tài khoản theo Google `sub` — bền vững hơn email khi người dùng đổi email Google. */
    Optional<User> findByGoogleId(String googleId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dùng cho kiểm tra trùng email khi cập nhật hồ sơ (UC-05), loại trừ chính user hiện tại. */
    boolean existsByEmailAndIdNot(String email, Long id);
}
