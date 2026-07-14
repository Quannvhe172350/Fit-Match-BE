package com.fitmatch.repository;

import com.fitmatch.entity.TrainingPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingPackageRepository extends JpaRepository<TrainingPackage, Long> {

    List<TrainingPackage> findByGymProfile_Id(Long gymProfileId);

    /** UC-009: catalog public — chỉ gói PUBLISHED của gym hiển thị. */
    List<TrainingPackage> findByGymProfile_IdAndStatus(Long gymProfileId, com.fitmatch.common.enums.CatalogStatus status);

    /** Ownership check theo operator đang đăng nhập (chống IDOR). */
    Optional<TrainingPackage> findByIdAndGymProfile_User_Username(Long id, String username);
}
