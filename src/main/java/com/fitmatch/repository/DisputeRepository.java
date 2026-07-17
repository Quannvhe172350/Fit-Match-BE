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

    /** P1-18 (UC-023): đếm tranh chấp liên quan tới một PT (monitor performance). */
    long countByBooking_PtProfile_Id(Long ptId);

    Page<Dispute> findByBooking_Customer_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByBooking_GymProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByBooking_PtProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByStatusOrderByIdDesc(DisputeStatus status, Pageable pageable);

    Page<Dispute> findAllByOrderByIdDesc(Pageable pageable);

    /** UC-076: đếm tranh chấp theo trạng thái trong khoảng; gymId null = toàn nền tảng. */
    @org.springframework.data.jpa.repository.Query(
            "select d.status, count(d) from Dispute d "
            + "where d.createdAt >= :from and d.createdAt < :to "
            + "and (:gymId is null or d.booking.gymProfile.id = :gymId) group by d.status")
    java.util.List<Object[]> countByStatusInRange(
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("gymId") Long gymId);
}
