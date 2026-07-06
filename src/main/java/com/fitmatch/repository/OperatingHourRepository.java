package com.fitmatch.repository;

import com.fitmatch.entity.OperatingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperatingHourRepository extends JpaRepository<OperatingHour, Long> {

    List<OperatingHour> findByGymBranch_IdOrderByDayOfWeek(Long branchId);

    void deleteByGymBranch_Id(Long branchId);
}
