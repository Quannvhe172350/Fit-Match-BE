package com.fitmatch.repository;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    Page<WithdrawalRequest> findByWallet_GymProfile_User_Username(String username, Pageable pageable);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);

    /** V94: số lệnh rút đang chờ duyệt — nguồn của chấm đỏ trên menu admin. */
    long countByStatus(WithdrawalStatus status);

    /** V61 — lệnh rút của một ví cụ thể; dùng chung cho cả ba loại chủ ví. */
    Page<WithdrawalRequest> findByWallet_IdOrderByIdDesc(Long walletId, Pageable pageable);

    /** V61 — khớp giao dịch CHI trên sao kê Casso về đúng lệnh rút. */
    Optional<WithdrawalRequest> findByRefCode(String refCode);

    /**
     * V61 — hàng đợi của Finance lọc theo loại chủ ví. Dùng derived query thay vì
     * một HQL với {@code :param is null} vì Hibernate không suy được kiểu enum của
     * tham số null; tầng service chọn nhánh theo bộ lọc nào được truyền.
     */
    Page<WithdrawalRequest> findByWallet_OwnerType(WalletOwnerType ownerType, Pageable pageable);

    Page<WithdrawalRequest> findByStatusAndWallet_OwnerType(
            WithdrawalStatus status, WalletOwnerType ownerType, Pageable pageable);
}
