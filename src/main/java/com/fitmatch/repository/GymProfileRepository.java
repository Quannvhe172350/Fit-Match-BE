package com.fitmatch.repository;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.repository.projection.GymDistanceView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface GymProfileRepository extends JpaRepository<GymProfile, Long>, JpaSpecificationExecutor<GymProfile> {

    Optional<GymProfile> findByUser_Username(String username);

    Optional<GymProfile> findByUser_UsernameAndVerificationStatus(String username, VerificationStatus status);

    boolean existsByUser_Username(String username);

    Page<GymProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    Optional<GymProfile> findByIdAndVerificationStatusAndActiveTrue(Long id, VerificationStatus status);

    /** UC-18 (V55): hồ sơ chưa có toạ độ — đầu vào cho job/endpoint backfill geocoding. */
    List<GymProfile> findByLatitudeIsNullAndAddressIsNotNull();

    // V60 — số liệu cho bảng theo dõi phủ toạ độ của Admin.
    long countByLatitudeIsNotNull();

    long countByCoordinatesPinnedTrue();

    @Query("select count(g) from GymProfile g where g.locationType = :locationType")
    long countByLocationType(@Param("locationType") String locationType);

    /**
     * V59/V60: hồ sơ có place_id nhưng toạ độ đã cũ — đầu vào của job làm mới.
     *
     * <p>Hai điều kiện loại trừ, mỗi cái chặn một kiểu hỏng khác nhau:
     * <ul>
     *   <li>{@code placeId is not null} — chỉ tra lại bản ghi biết CHÍNH XÁC mình
     *       là địa điểm nào của Google; geocode lại chuỗi địa chỉ có thể ra một
     *       nơi khác hẳn.</li>
     *   <li>{@code coordinatesPinned = false} — chủ gym đã kéo ghim tay thì đó là
     *       toạ độ đúng nhất hệ thống có; job kéo về chỗ Google nói là phá.</li>
     * </ul>
     */
    List<GymProfile> findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(
            java.time.LocalDateTime before);

    /**
     * UC-18 (V55) — tìm gym theo bán kính quanh một toạ độ.
     *
     * <p>Một gym khớp khi trụ sở HOẶC bất kỳ chi nhánh đang hoạt động nào nằm
     * trong bán kính; {@code distanceKm} trả về là khoảng cách của điểm gần nhất
     * (MIN sau khi UNION hai nguồn toạ độ).
     *
     * <p>Hai tầng lọc vì lý do hiệu năng: bounding box {@code minLat..maxLat} /
     * {@code minLng..maxLng} chạy trên index (latitude, longitude) để loại phần
     * lớn bản ghi, sau đó haversine mới cắt chính xác theo hình tròn. Caller sinh
     * hộp bằng {@code GeoUtils.latDelta/lngDelta}.
     *
     * <p>{@code LEAST/GREATEST} bọc quanh đối số của ACOS: sai số dấu phẩy động có
     * thể đẩy giá trị ra ngoài [-1, 1] khi hai điểm gần như trùng nhau, khi đó
     * ACOS trả NULL và gym biến mất khỏi kết quả một cách khó hiểu.
     *
     * <p>Các tham số lọc còn lại đều nullable — truyền null nghĩa là không lọc.
     * {@code keyword/city/district} phải được caller chuẩn hoá sẵn thành pattern
     * LIKE viết thường (ví dụ {@code %california%}).
     */
    @Query(value = """
            SELECT p.gym_id AS gymId, MIN(p.distance_km) AS distanceKm
            FROM (
                SELECT g.id AS gym_id,
                       6371 * ACOS(LEAST(1, GREATEST(-1,
                           COS(RADIANS(:lat)) * COS(RADIANS(g.latitude))
                             * COS(RADIANS(g.longitude) - RADIANS(:lng))
                         + SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude))))) AS distance_km
                  FROM gym_profiles g
                 WHERE g.latitude BETWEEN :minLat AND :maxLat
                   AND g.longitude BETWEEN :minLng AND :maxLng
                UNION ALL
                SELECT b.gym_profile_id AS gym_id,
                       6371 * ACOS(LEAST(1, GREATEST(-1,
                           COS(RADIANS(:lat)) * COS(RADIANS(b.latitude))
                             * COS(RADIANS(b.longitude) - RADIANS(:lng))
                         + SIN(RADIANS(:lat)) * SIN(RADIANS(b.latitude))))) AS distance_km
                  FROM gym_branches b
                 WHERE b.active = 1
                   AND b.latitude BETWEEN :minLat AND :maxLat
                   AND b.longitude BETWEEN :minLng AND :maxLng
            ) p
            JOIN gym_profiles gp ON gp.id = p.gym_id
            WHERE p.distance_km <= :radiusKm
              AND gp.verification_status = 'APPROVED'
              AND gp.active = 1
              AND (:keyword IS NULL
                   OR LOWER(gp.gym_name) LIKE :keyword
                   OR LOWER(COALESCE(gp.description, '')) LIKE :keyword)
              AND (:city IS NULL OR LOWER(COALESCE(gp.city, '')) LIKE :city)
              AND (:district IS NULL
                   OR LOWER(COALESCE(gp.district, '')) LIKE :district
                   OR LOWER(COALESCE(gp.address, '')) LIKE :district)
              AND ((:minPrice IS NULL AND :maxPrice IS NULL)
                   OR EXISTS (SELECT 1 FROM training_packages tp
                               WHERE tp.gym_profile_id = gp.id
                                 AND tp.status = 'PUBLISHED'
                                 AND (:minPrice IS NULL OR tp.price >= :minPrice)
                                 AND (:maxPrice IS NULL OR tp.price <= :maxPrice)))
            GROUP BY p.gym_id
            ORDER BY distanceKm ASC, p.gym_id ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.gym_id)
            FROM (
                SELECT g.id AS gym_id,
                       6371 * ACOS(LEAST(1, GREATEST(-1,
                           COS(RADIANS(:lat)) * COS(RADIANS(g.latitude))
                             * COS(RADIANS(g.longitude) - RADIANS(:lng))
                         + SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude))))) AS distance_km
                  FROM gym_profiles g
                 WHERE g.latitude BETWEEN :minLat AND :maxLat
                   AND g.longitude BETWEEN :minLng AND :maxLng
                UNION ALL
                SELECT b.gym_profile_id AS gym_id,
                       6371 * ACOS(LEAST(1, GREATEST(-1,
                           COS(RADIANS(:lat)) * COS(RADIANS(b.latitude))
                             * COS(RADIANS(b.longitude) - RADIANS(:lng))
                         + SIN(RADIANS(:lat)) * SIN(RADIANS(b.latitude))))) AS distance_km
                  FROM gym_branches b
                 WHERE b.active = 1
                   AND b.latitude BETWEEN :minLat AND :maxLat
                   AND b.longitude BETWEEN :minLng AND :maxLng
            ) p
            JOIN gym_profiles gp ON gp.id = p.gym_id
            WHERE p.distance_km <= :radiusKm
              AND gp.verification_status = 'APPROVED'
              AND gp.active = 1
              AND (:keyword IS NULL
                   OR LOWER(gp.gym_name) LIKE :keyword
                   OR LOWER(COALESCE(gp.description, '')) LIKE :keyword)
              AND (:city IS NULL OR LOWER(COALESCE(gp.city, '')) LIKE :city)
              AND (:district IS NULL
                   OR LOWER(COALESCE(gp.district, '')) LIKE :district
                   OR LOWER(COALESCE(gp.address, '')) LIKE :district)
              AND ((:minPrice IS NULL AND :maxPrice IS NULL)
                   OR EXISTS (SELECT 1 FROM training_packages tp
                               WHERE tp.gym_profile_id = gp.id
                                 AND tp.status = 'PUBLISHED'
                                 AND (:minPrice IS NULL OR tp.price >= :minPrice)
                                 AND (:maxPrice IS NULL OR tp.price <= :maxPrice)))
            """,
            nativeQuery = true)
    Page<GymDistanceView> searchNearby(@Param("lat") double lat,
                                       @Param("lng") double lng,
                                       @Param("radiusKm") double radiusKm,
                                       @Param("minLat") double minLat,
                                       @Param("maxLat") double maxLat,
                                       @Param("minLng") double minLng,
                                       @Param("maxLng") double maxLng,
                                       @Param("keyword") String keyword,
                                       @Param("city") String city,
                                       @Param("district") String district,
                                       @Param("minPrice") BigDecimal minPrice,
                                       @Param("maxPrice") BigDecimal maxPrice,
                                       Pageable pageable);
}
