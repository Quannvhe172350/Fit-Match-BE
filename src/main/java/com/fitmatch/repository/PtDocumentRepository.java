package com.fitmatch.repository;

import com.fitmatch.entity.PtDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PtDocumentRepository extends JpaRepository<PtDocument, Long> {

    List<PtDocument> findByPtProfile_Id(Long ptProfileId);

    void deleteByPtProfile_Id(Long ptProfileId);

    /** UC-020: định vị tài liệu trong phạm vi một PT (Gym quản lý). */
    Optional<PtDocument> findByIdAndPtProfile_Id(Long id, Long ptProfileId);
}
