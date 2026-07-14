package com.fitmatch.repository;

import com.fitmatch.entity.GymService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymServiceRepository extends JpaRepository<GymService, Long> {

    List<GymService> findByGymProfile_Id(Long gymProfileId);

    /** UC-009: catalog public — chỉ dịch vụ PUBLISHED của gym hiển thị. */
    List<GymService> findByGymProfile_IdAndStatus(Long gymProfileId, com.fitmatch.common.enums.CatalogStatus status);

    Optional<GymService> findByIdAndGymProfile_User_Username(Long id, String username);
}
