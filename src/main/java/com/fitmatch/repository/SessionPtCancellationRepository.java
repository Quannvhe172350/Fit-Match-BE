package com.fitmatch.repository;

import com.fitmatch.common.enums.PtCancellationStatus;
import com.fitmatch.entity.SessionPtCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionPtCancellationRepository extends JpaRepository<SessionPtCancellation, Long> {

    Optional<SessionPtCancellation> findFirstByTrainingSession_IdAndStatusOrderByIdDesc(
            Long sessionId, PtCancellationStatus status);

    List<SessionPtCancellation> findByTrainingSession_IdInAndStatus(
            Collection<Long> sessionIds, PtCancellationStatus status);

    /**
     * Job chốt tự động: buổi tới ngày mà khách vẫn chưa quyết. Lọc theo ngày
     * tập của buổi chứ không theo ngày tạo — khách có bao nhiêu thời gian là do
     * đơn nghỉ được duyệt sớm hay muộn.
     */
    @Query("select c from SessionPtCancellation c join fetch c.trainingSession s "
            + "where c.status = :status and s.sessionDate <= :cutoff")
    List<SessionPtCancellation> findPendingDueBy(@Param("status") PtCancellationStatus status,
                                                 @Param("cutoff") java.time.LocalDate cutoff);

    /** Tổng đã hoàn lẻ của một vé — chốt chặn thứ hai cho bất biến tiền. */
    @Query("select coalesce(sum(c.refundAmount), 0) from SessionPtCancellation c "
            + "where c.trainingSession.ticket.id = :ticketId and c.status = :status")
    java.math.BigDecimal sumRefundedOfTicket(@Param("ticketId") Long ticketId,
                                             @Param("status") PtCancellationStatus status);
}
