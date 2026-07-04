package com.fitmatch.service;

import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtDocumentResponse;

import java.util.List;

/**
 * UC-020: Gym ghi nhận chứng chỉ, kinh nghiệm và tài liệu năng lực của PT thuộc quyền mình.
 */
public interface GymPtQualificationService {

    CertificationResponse addCertification(String gymUsername, Long ptId, CertificationRequest request);

    CertificationResponse updateCertification(String gymUsername, Long ptId, Long certId, CertificationRequest request);

    void deleteCertification(String gymUsername, Long ptId, Long certId);

    List<CertificationResponse> listCertifications(String gymUsername, Long ptId);

    PtDocumentResponse addDocument(String gymUsername, Long ptId, PtDocumentDto request);

    void deleteDocument(String gymUsername, Long ptId, Long documentId);

    List<PtDocumentResponse> listDocuments(String gymUsername, Long ptId);
}
