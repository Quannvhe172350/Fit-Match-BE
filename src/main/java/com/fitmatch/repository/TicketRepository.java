package com.fitmatch.repository;

import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndCustomer_Username(Long id, String username);

    Optional<Ticket> findByIdAndGymProfile_User_Username(Long id, String username);

    Page<Ticket> findByCustomer_Username(String username, Pageable pageable);

    Page<Ticket> findByCustomer_UsernameAndStatus(String username, TicketStatus status, Pageable pageable);

    /** Câu 32: vé quá hạn mà chưa dùng hết — TicketExpiryJob chuyển sang EXPIRED. */
    List<Ticket> findByStatusAndExpiresAtBefore(TicketStatus status, LocalDateTime cutoff);

    /** Vé sắp hết hạn trong một cửa sổ hẹp — dùng để nhắc khách đúng một lần. */
    List<Ticket> findByStatusAndExpiresAtBetween(TicketStatus status, LocalDateTime from, LocalDateTime to);

    /** Vé đã hết holding period, đến hạn giải ngân về gym. */
    List<Ticket> findBySettlementStatusAndSettlementPendingAtBefore(
            SettlementStatus settlementStatus, LocalDateTime cutoff);

    /**
     * Khoá ghi bản ghi vé để tuần tự hoá việc mở tranh chấp — giữ nguyên cách làm
     * của booking cũ: MariaDB không có partial unique index nên hai luồng cùng mở
     * tranh chấp phải xếp hàng, tránh double-freeze quỹ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> lockById(@Param("id") Long id);

    /** UC-076: đếm vé theo trạng thái trong khoảng thời gian; gymId null = toàn nền tảng. */
    @Query("select t.status, count(t) from Ticket t "
            + "where t.createdAt >= :from and t.createdAt < :to "
            + "and (:gymId is null or t.gymProfile.id = :gymId) group by t.status")
    List<Object[]> countByStatusInRange(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("gymId") Long gymId);
}
