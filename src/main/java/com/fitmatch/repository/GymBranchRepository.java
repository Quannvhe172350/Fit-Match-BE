package com.fitmatch.repository;

import com.fitmatch.entity.GymBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymBranchRepository extends JpaRepository<GymBranch, Long> {

    List<GymBranch> findByGymProfile_Id(Long gymProfileId);

    /** UC-009: chi nhánh đang hoạt động cho trang public. */
    List<GymBranch> findByGymProfile_IdAndActiveTrue(Long gymProfileId);

    /**
     * UC-18 (V55): nạp chi nhánh của cả một trang kết quả trong 1 truy vấn —
     * tìm theo bán kính cần toạ độ chi nhánh để chọn điểm gần nhất làm marker,
     * lặp findByGymProfile_Id cho từng gym sẽ thành N+1.
     */
    List<GymBranch> findByGymProfile_IdInAndActiveTrue(java.util.Collection<Long> gymProfileIds);

    /** UC-18 (V55): chi nhánh chưa có toạ độ — đầu vào cho backfill geocoding. */
    List<GymBranch> findByLatitudeIsNullAndAddressIsNotNull();

    /**
     * UC-030: khoá chi nhánh khi kiểm tra capacity lúc checkout/reschedule để
     * tuần tự hoá các giữ chỗ đồng thời (chống overbooking). Thứ tự khoá cố
     * định toàn hệ thống: PT trước, chi nhánh sau (tránh deadlock).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from GymBranch b where b.id = :id")
    Optional<GymBranch> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<GymBranch> findByIdAndGymProfile_User_Username(Long id, String username);
}
