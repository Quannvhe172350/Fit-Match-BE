package com.fitmatch.repository;

import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Câu 17: mỗi vé đúng một đánh giá phòng gym. */
    boolean existsByTicket_Id(Long ticketId);

    /** Câu 36: mỗi buổi tập đúng một đánh giá PT. */
    boolean existsBySession_Id(Long sessionId);

    Optional<Review> findByIdAndCustomer_Username(Long id, String username);

    Page<Review> findByCustomer_UsernameOrderByIdDesc(String username, Pageable pageable);

    Page<Review> findByGymProfile_IdAndStatusOrderByIdDesc(Long gymProfileId, ReviewStatus status, Pageable pageable);

    Page<Review> findByPtProfile_IdAndStatusOrderByIdDesc(Long ptProfileId, ReviewStatus status, Pageable pageable);

    /** Review của các gym do một operator sở hữu (mọi trạng thái) — cho gym theo dõi chất lượng. */
    Page<Review> findByGymProfile_User_UsernameOrderByIdDesc(String gymUsername, Pageable pageable);

    /** UC-071: điểm TB + số lượng review VISIBLE của một gym (chỉ tính công khai). */
    /**
     * Đánh giá PT cũng lưu gymProfile để hiện đúng ngữ cảnh phòng tập, nên điểm
     * của GYM phải loại chúng ra — nếu không, một PT được 5 sao sẽ tự đẩy điểm
     * phòng gym lên. targetType null = dữ liệu trước V76, đều là đánh giá gym.
     */
    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r "
            + "where r.gymProfile.id = :gymId and r.status = com.fitmatch.common.enums.ReviewStatus.VISIBLE "
            + "and (r.targetType is null or r.targetType = com.fitmatch.common.enums.ReviewTargetType.GYM)")
    Object[] aggregateGym(@Param("gymId") Long gymId);

    /** Trang phòng gym chỉ hiện đánh giá GYM; đánh giá PT nằm ở trang PT. */
    @Query("select r from Review r where r.gymProfile.id = :gymId and r.status = :status "
            + "and (r.targetType is null or r.targetType = com.fitmatch.common.enums.ReviewTargetType.GYM) "
            + "order by r.id desc")
    Page<Review> findVisibleGymReviews(@Param("gymId") Long gymId,
                                       @Param("status") ReviewStatus status, Pageable pageable);

    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r "
            + "where r.ptProfile.id = :ptId and r.status = com.fitmatch.common.enums.ReviewStatus.VISIBLE")
    Object[] aggregatePt(@Param("ptId") Long ptId);

    /**
     * Phổ điểm 1..5 sao của một gym — trả về các cặp (rating, count) cho biểu đồ
     * thanh trên trang chi tiết. Gom nhóm ở DB thay vì tải hết review về đếm.
     */
    @Query("select r.rating, count(r) from Review r "
            + "where r.gymProfile.id = :gymId and r.status = com.fitmatch.common.enums.ReviewStatus.VISIBLE "
            + "and (r.targetType is null or r.targetType = com.fitmatch.common.enums.ReviewTargetType.GYM) "
            + "group by r.rating")
    List<Object[]> ratingDistributionGym(@Param("gymId") Long gymId);

    @Query("select r.rating, count(r) from Review r "
            + "where r.ptProfile.id = :ptId and r.status = com.fitmatch.common.enums.ReviewStatus.VISIBLE "
            + "group by r.rating")
    List<Object[]> ratingDistributionPt(@Param("ptId") Long ptId);
}
