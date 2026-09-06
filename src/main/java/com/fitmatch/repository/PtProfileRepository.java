package com.fitmatch.repository;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PtProfileRepository extends JpaRepository<PtProfile, Long>, JpaSpecificationExecutor<PtProfile> {

    Optional<PtProfile> findByUser_Username(String username);

    /** UC-021 (P0-3): tra hồ sơ PT theo User.id — dùng cho admin thao tác từ trang quản lý user. */
    Optional<PtProfile> findByUser_Id(Long userId);

    boolean existsByUser_Username(String username);

    Optional<PtProfile> findByIdAndVerificationStatusAndActiveTrue(Long id, VerificationStatus status);

    Page<PtProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    /** UC-021: PT hiển thị công khai — ACTIVE và thuộc Gym APPROVED đang hiển thị. */
    Optional<PtProfile> findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
            Long id, PtStatus status, VerificationStatus gymStatus);

    /** UC-019: danh sách PT thuộc một Gym. */
    Page<PtProfile> findByGymProfile_Id(Long gymProfileId, Pageable pageable);

    /** UC-019: PT thuộc Gym, thu hẹp theo chi nhánh đang lọc (id lấy từ pt_assignments). */
    Page<PtProfile> findByGymProfile_IdAndIdIn(Long gymProfileId, java.util.Collection<Long> ids,
                                               Pageable pageable);

    /** Job cảnh báo lịch rảnh mỏng: quét toàn bộ PT đang hoạt động. */
    java.util.List<PtProfile> findByStatus(PtStatus status);

    /** UC-009: PT khả dụng của gym cho trang public / chọn khi booking. */
    Page<PtProfile> findByGymProfile_IdAndStatus(Long gymProfileId, PtStatus status, Pageable pageable);

    /** Bug S2-04: PT khả dụng của gym, thu hẹp theo chi nhánh khách đã chọn. */
    Page<PtProfile> findByGymProfile_IdAndStatusAndIdIn(Long gymProfileId, PtStatus status,
                                                        java.util.Collection<Long> ids, Pageable pageable);

    /** UC-019: PT thuộc Gym của operator đang đăng nhập (ownership check, chống IDOR). */
    Optional<PtProfile> findByIdAndGymProfile_User_Username(Long id, String username);

    /**
     * UC-033: khoá bản ghi PT (PESSIMISTIC_WRITE) để tuần tự hoá việc giữ chỗ,
     * tránh double-booking khi hai request checkout/accept chạy song song.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PtProfile p where p.id = :id")
    Optional<PtProfile> lockById(@Param("id") Long id);
}
