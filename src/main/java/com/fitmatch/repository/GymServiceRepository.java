package com.fitmatch.repository;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.entity.GymService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymServiceRepository extends JpaRepository<GymService, Long> {

    List<GymService> findByGymProfile_IdOrderByIdDesc(Long gymProfileId);

    Optional<GymService> findByIdAndGymProfile_User_Username(Long id, String username);

    /** Marketplace/checkout: dịch vụ còn bán của một gym. */
    List<GymService> findByGymProfile_IdAndStatusAndActiveTrueOrderByIdDesc(
            Long gymProfileId, CatalogStatus status);
}
