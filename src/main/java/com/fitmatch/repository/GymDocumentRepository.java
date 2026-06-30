package com.fitmatch.repository;

import com.fitmatch.entity.GymDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GymDocumentRepository extends JpaRepository<GymDocument, Long> {

    List<GymDocument> findByGymProfile_Id(Long gymProfileId);

    void deleteByGymProfile_Id(Long gymProfileId);
}
