package com.fitmatch.repository;

import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.entity.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    /** Còn tranh chấp chưa đóng cho booking — chống mở trùng. */
    boolean existsByBooking_IdAndStatusIn(Long bookingId, List<DisputeStatus> statuses);

    Page<Dispute> findByBooking_Customer_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByBooking_GymProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByBooking_PtProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByStatusOrderByIdDesc(DisputeStatus status, Pageable pageable);

    Page<Dispute> findAllByOrderByIdDesc(Pageable pageable);
}
