package com.fitmatch.repository;

import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /** Idempotency: giao dịch Casso đã xử lý chưa. */
    boolean existsByExternalId(String externalId);

    // UC-053 — hàng đợi đối soát. Dùng derived query cho từng tổ hợp filter thay vì
    // một JPQL kiểu "(:p is null or col = :p)": Hibernate suy kiểu tham số enum từ
    // biểu thức so sánh, bind null vào đó là chỗ dễ vỡ không đáng đánh cược.

    Page<PaymentTransaction> findByReconStatus(ReconStatus reconStatus, Pageable pageable);

    Page<PaymentTransaction> findByAnomaly(PaymentTxnAnomaly anomaly, Pageable pageable);

    Page<PaymentTransaction> findByReconStatusAndAnomaly(ReconStatus reconStatus,
                                                         PaymentTxnAnomaly anomaly,
                                                         Pageable pageable);

    /**
     * Tổng quan hàng đợi: mỗi dòng là {@code [anomaly, số giao dịch, tổng tiền]}
     * của các giao dịch ở trạng thái truyền vào — dùng cho thẻ thống kê màn đối soát.
     */
    @Query("""
            select t.anomaly, count(t), coalesce(sum(t.amount), 0)
            from PaymentTransaction t
            where t.reconStatus = :reconStatus
            group by t.anomaly
            """)
    List<Object[]> summarizeByAnomaly(@Param("reconStatus") ReconStatus reconStatus);
}
