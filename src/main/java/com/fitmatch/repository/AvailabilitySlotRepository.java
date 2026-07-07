package com.fitmatch.repository;

import com.fitmatch.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    List<AvailabilitySlot> findByPtProfile_IdOrderByDayOfWeekAscStartTimeAsc(Long ptProfileId);

    List<AvailabilitySlot> findByPtProfile_IdAndDayOfWeek(Long ptProfileId, Integer dayOfWeek);

    void deleteByPtProfile_Id(Long ptProfileId);
}
