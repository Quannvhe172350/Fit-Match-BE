package com.fitmatch.repository;

import com.fitmatch.entity.PtCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PtCertificationRepository extends JpaRepository<PtCertification, Long> {

    List<PtCertification> findByPtProfile_Id(Long ptProfileId);

    /** Dùng để vừa định vị chứng chỉ vừa kiểm tra quyền sở hữu theo user. */
    Optional<PtCertification> findByIdAndPtProfile_User_Username(Long id, String username);
}
