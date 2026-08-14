package com.fitmatch.repository;

import com.fitmatch.entity.PtAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PtAvailabilityRepository extends JpaRepository<PtAvailability, Long> {

    List<PtAvailability> findByPtProfile_IdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Long ptProfileId, LocalDate from, LocalDate to);

    /** PtSlotValidator: khung giờ khách chọn phải khớp đúng một khung PT đã khai. */
    Optional<PtAvailability> findByPtProfile_IdAndSlotDateAndStartTime(
            Long ptProfileId, LocalDate slotDate, LocalTime startTime);

    /** Lưới ngày x giờ gộp nhiều PT của một chi nhánh. */
    List<PtAvailability> findByPtProfile_IdInAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Collection<Long> ptProfileIds, LocalDate from, LocalDate to);

    /** Chọn giờ trước rồi lọc PT: ai đã khai đúng khung này trong nhóm PT của chi nhánh. */
    List<PtAvailability> findByPtProfile_IdInAndSlotDateAndStartTime(
            Collection<Long> ptProfileIds, LocalDate slotDate, LocalTime startTime);

    /**
     * Quyết định #8: đếm số NGÀY đã khai (không phải số khung giờ) kể từ hôm nay —
     * dưới ngưỡng thì cảnh báo nhưng vẫn lưu và vẫn cho khách đặt.
     */
    @Query("select count(distinct a.slotDate) from PtAvailability a "
            + "where a.ptProfile.id = :ptId and a.slotDate >= :from")
    long countDistinctDaysFrom(@Param("ptId") Long ptProfileId, @Param("from") LocalDate from);

    void deleteByPtProfile_IdAndSlotDateBetween(Long ptProfileId, LocalDate from, LocalDate to);
}
