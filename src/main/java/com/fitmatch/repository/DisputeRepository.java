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



    /** Còn tranh chấp chưa đóng cho vé — chống mở trùng (mô hình vé). */
    boolean existsByTicket_IdAndStatusIn(Long ticketId, List<DisputeStatus> statuses);

    Page<Dispute> findByTicket_Customer_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Dispute> findByTicket_GymProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    /**
     * PT chỉ là bên liên quan của tranh chấp CẤP BUỔI — tranh chấp cấp vé có thể
     * trải nhiều PT nên không quy về một người được.
     */
    Page<Dispute> findBySession_PtProfile_User_UsernameOrderByIdDesc(String username, Pageable pageable);

    /** P1-18 (UC-023): đếm tranh chấp liên quan tới một PT (monitor performance). */
    long countBySession_PtProfile_Id(Long ptId);




    Page<Dispute> findByStatusOrderByIdDesc(DisputeStatus status, Pageable pageable);

    Page<Dispute> findAllByOrderByIdDesc(Pageable pageable);

    /** UC-076: đếm tranh chấp theo trạng thái trong khoảng; gymId null = toàn nền tảng. */
    @org.springframework.data.jpa.repository.Query(
            "select d.status, count(d) from Dispute d "
            + "where d.createdAt >= :from and d.createdAt < :to "
            + "and (:gymId is null or d.ticket.gymProfile.id = :gymId) group by d.status")
    java.util.List<Object[]> countByStatusInRange(
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("gymId") Long gymId);
}
