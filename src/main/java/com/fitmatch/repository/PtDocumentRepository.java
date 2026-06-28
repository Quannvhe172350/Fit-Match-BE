package com.fitmatch.repository;

import com.fitmatch.entity.PtDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PtDocumentRepository extends JpaRepository<PtDocument, Long> {

    List<PtDocument> findByPtProfile_Id(Long ptProfileId);

    void deleteByPtProfile_Id(Long ptProfileId);
}
