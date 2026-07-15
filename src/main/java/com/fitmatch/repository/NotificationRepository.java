package com.fitmatch.repository;

import com.fitmatch.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_UsernameOrderByIdDesc(String username, Pageable pageable);

    long countByUser_UsernameAndReadAtIsNull(String username);

    java.util.Optional<Notification> findByIdAndUser_Username(Long id, String username);

    @Modifying
    @Query("update Notification n set n.readAt = :now "
            + "where n.user.username = :username and n.readAt is null")
    int markAllRead(@Param("username") String username, @Param("now") LocalDateTime now);
}
