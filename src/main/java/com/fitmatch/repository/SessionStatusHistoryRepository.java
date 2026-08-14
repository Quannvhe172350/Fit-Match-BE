package com.fitmatch.repository;

import com.fitmatch.entity.SessionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionStatusHistoryRepository extends JpaRepository<SessionStatusHistory, Long> {

    List<SessionStatusHistory> findBySession_IdOrderByCreatedAtAsc(Long sessionId);
}
