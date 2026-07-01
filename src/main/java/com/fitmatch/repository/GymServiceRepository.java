package com.fitmatch.repository;

import com.fitmatch.entity.GymService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymServiceRepository extends JpaRepository<GymService, Long> {

    List<GymService> findByGymProfile_Id(Long gymProfileId);

    Optional<GymService> findByIdAndGymProfile_User_Username(Long id, String username);
}
