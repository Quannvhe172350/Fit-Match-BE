package com.fitmatch.repository;

import com.fitmatch.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByGymProfile_Id(Long gymProfileId);

    Optional<Wallet> findByGymProfile_User_Username(String username);

    /** V61 — ví khách hàng theo user. */
    Optional<Wallet> findByUser_Id(Long userId);

    /** V61 — ví khách hàng theo tài khoản đăng nhập. */
    Optional<Wallet> findByUser_Username(String username);

    /** Khoá ví khi cập nhật số dư để tuần tự hoá bút toán đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> lockById(@Param("id") Long id);

    /**
     * Đọc lại ví Gym sau khi tạo ví lần đầu. PHẢI là truy vấn khoá: dưới
     * REPEATABLE READ (mặc định của InnoDB), SELECT thường vẫn trả về snapshot
     * chụp từ lần đọc đầu transaction nên KHÔNG thấy ví do request song song vừa
     * commit — đọc lại bằng {@code findByGymProfile_Id} sẽ rỗng y như cũ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.gymProfile.id = :gymProfileId")
    Optional<Wallet> lockByGymProfileId(@Param("gymProfileId") Long gymProfileId);

    /** V61 — đọc lại ví khách hàng bằng truy vấn khoá; xem {@link #lockByGymProfileId}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> lockByUserId(@Param("userId") Long userId);
}
