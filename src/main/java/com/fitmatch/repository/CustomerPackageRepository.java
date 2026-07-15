package com.fitmatch.repository;

import com.fitmatch.entity.CustomerPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPackageRepository extends JpaRepository<CustomerPackage, Long> {

    List<CustomerPackage> findByCustomer_UsernameOrderByIdDesc(String username);

    Optional<CustomerPackage> findByIdAndCustomer_Username(Long id, String username);

    boolean existsByPurchaseBooking_Id(Long bookingId);

    /**
     * Khoá bản ghi gói (PESSIMISTIC_WRITE) để tuần tự hoá việc đếm buổi đang giữ
     * chỗ + tiêu buổi khi nhiều booking cùng gói checkout đồng thời (UC-049).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cp from CustomerPackage cp where cp.id = :id")
    Optional<CustomerPackage> lockById(@Param("id") Long id);
}
