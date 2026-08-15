package com.fitmatch.repository;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.entity.TicketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    Page<TicketType> findByGymProfile_User_Username(String username, Pageable pageable);

    List<TicketType> findByGymProfile_IdOrderByIdDesc(Long gymProfileId);

    Optional<TicketType> findByIdAndGymProfile_User_Username(Long id, String username);

    /**
     * Marketplace: vé đang bán tại một chi nhánh. Đi qua bảng nối nên phải
     * {@code distinct} — một loại vé chỉ được xuất hiện một lần dù gắn nhiều chi nhánh.
     */
    List<TicketType> findDistinctByBranches_GymBranch_IdAndStatusAndActiveTrue(
            Long branchId, CatalogStatus status);

    /** Kiểm tra vé còn bán ở chi nhánh khách chọn hay không, trước khi cho mua. */
    boolean existsByIdAndBranches_GymBranch_Id(Long ticketTypeId, Long branchId);

    /**
     * Marketplace: duyệt vé đang bán TRÊN TOÀN SÀN, lọc theo loại/khu vực/giá.
     *
     * <p>Trước đây FE không có endpoint này nên trang /packages phải tìm 100 gym
     * rồi gọi catalog của từng gym (fan-out 100 request, 6 luồng song song) và
     * chỉ quét được một phần sàn — trang phải hiện cảnh báo "mới quét N gym".
     * Gộp về một truy vấn thì bộ lọc và phân trang mới đúng trên toàn tập.
     *
     * <p>{@code distinct} là bắt buộc: một loại vé bán ở nhiều chi nhánh sẽ nhân
     * dòng qua bảng nối.
     *
     * <p>Lọc theo trạng thái GYM chứ không chỉ trạng thái vé — cùng luật với
     * {@code GymProfileSpecifications.visibleOnMarketplace()}. Thiếu điều kiện này
     * thì vé của gym chưa duyệt hoặc đang tắt vẫn nằm trên trang duyệt vé công
     * khai; khách mua xong mới biết vì validator đặt lịch từ chối với lý do
     * "Phòng gym hiện không nhận đặt lịch".
     */
    @Query("""
            select distinct t from TicketType t
            join t.gymProfile g
            where t.status = com.fitmatch.common.enums.CatalogStatus.PUBLISHED
              and t.active = true
              and g.verificationStatus = com.fitmatch.common.enums.VerificationStatus.APPROVED
              and g.active = true
              and (:kind is null or t.kind = :kind)
              and (:city is null or g.city = :city)
              and (:district is null or g.district = :district)
              and (:minPrice is null or t.price >= :minPrice)
              and (:maxPrice is null or t.price <= :maxPrice)
              and (:keyword is null
                   or lower(t.name) like lower(concat('%', :keyword, '%'))
                   or lower(g.gymName) like lower(concat('%', :keyword, '%')))
            """)
    Page<TicketType> searchPublic(@Param("kind") TicketKind kind,
                                  @Param("keyword") String keyword,
                                  @Param("city") String city,
                                  @Param("district") String district,
                                  @Param("minPrice") BigDecimal minPrice,
                                  @Param("maxPrice") BigDecimal maxPrice,
                                  Pageable pageable);
}
