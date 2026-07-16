package com.fitmatch.repository;

import com.fitmatch.entity.GymDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymDocumentRepository extends JpaRepository<GymDocument, Long> {

    List<GymDocument> findByGymProfile_Id(Long gymProfileId);

    void deleteByGymProfile_Id(Long gymProfileId);

    /** P1-20: tài liệu thuộc gym của chính user đăng nhập (chống IDOR). */
    Optional<GymDocument> findByIdAndGymProfile_User_Username(Long id, String username);
}
