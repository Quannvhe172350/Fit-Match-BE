package com.fitmatch.repository;

import com.fitmatch.entity.SessionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionNoteRepository extends JpaRepository<SessionNote, Long> {

    List<SessionNote> findByBooking_IdOrderByIdAsc(Long bookingId);
}
