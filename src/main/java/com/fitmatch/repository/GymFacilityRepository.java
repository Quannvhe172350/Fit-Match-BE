package com.fitmatch.repository;

import com.fitmatch.entity.GymFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymFacilityRepository extends JpaRepository<GymFacility, Long> {

    List<GymFacility> findByGymProfile_Id(Long gymProfileId);

    /** Vừa định vị vừa kiểm tra quyền sở hữu theo user (chống IDOR). */
    Optional<GymFacility> findByIdAndGymProfile_User_Username(Long id, String username);
}
