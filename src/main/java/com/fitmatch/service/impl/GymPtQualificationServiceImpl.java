package com.fitmatch.service.impl;

import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtDocumentResponse;
import com.fitmatch.entity.PtCertification;
import com.fitmatch.entity.PtDocument;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.GymPtQualificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymPtQualificationServiceImpl implements GymPtQualificationService {

    private final PtProfileRepository ptProfileRepository;
    private final PtCertificationRepository ptCertificationRepository;
    private final PtDocumentRepository ptDocumentRepository;

    @Override
    @Transactional
    public CertificationResponse addCertification(String gymUsername, Long ptId, CertificationRequest request) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);
        PtCertification cert = ptCertificationRepository.save(PtCertification.builder()
                .ptProfile(pt)
                .name(request.getName())
                .issuingOrganization(request.getIssuingOrganization())
                .issueDate(request.getIssueDate())
                .expiryDate(request.getExpiryDate())
                .credentialUrl(request.getCredentialUrl())
                .build());
        log.info("Certification {} added to PT {} by gym {}", cert.getId(), ptId, gymUsername);
        return CertificationResponse.of(cert);
    }

    @Override
    @Transactional
    public CertificationResponse updateCertification(String gymUsername, Long ptId, Long certId,
                                                     CertificationRequest request) {
        requireOwnedPt(gymUsername, ptId);
        PtCertification cert = ptCertificationRepository.findByIdAndPtProfile_Id(certId, ptId)
                .orElseThrow(() -> new ResourceNotFoundException("Certification", certId));
        cert.setName(request.getName());
        cert.setIssuingOrganization(request.getIssuingOrganization());
        cert.setIssueDate(request.getIssueDate());
        cert.setExpiryDate(request.getExpiryDate());
        cert.setCredentialUrl(request.getCredentialUrl());
        ptCertificationRepository.save(cert);
        log.info("Certification {} of PT {} updated by gym {}", certId, ptId, gymUsername);
        return CertificationResponse.of(cert);
    }

    @Override
    @Transactional
    public void deleteCertification(String gymUsername, Long ptId, Long certId) {
        requireOwnedPt(gymUsername, ptId);
        PtCertification cert = ptCertificationRepository.findByIdAndPtProfile_Id(certId, ptId)
                .orElseThrow(() -> new ResourceNotFoundException("Certification", certId));
        ptCertificationRepository.delete(cert);
        log.info("Certification {} of PT {} deleted by gym {}", certId, ptId, gymUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificationResponse> listCertifications(String gymUsername, Long ptId) {
        requireOwnedPt(gymUsername, ptId);
        return ptCertificationRepository.findByPtProfile_Id(ptId).stream()
                .map(CertificationResponse::of).toList();
    }

    @Override
    @Transactional
    public PtDocumentResponse addDocument(String gymUsername, Long ptId, PtDocumentDto request) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);
        PtDocument doc = ptDocumentRepository.save(PtDocument.builder()
                .ptProfile(pt)
                .documentType(request.getDocumentType())
                .fileUrl(request.getFileUrl())
                .build());
        log.info("Document {} added to PT {} by gym {}", doc.getId(), ptId, gymUsername);
        return PtDocumentResponse.of(doc);
    }

    @Override
    @Transactional
    public void deleteDocument(String gymUsername, Long ptId, Long documentId) {
        requireOwnedPt(gymUsername, ptId);
        PtDocument doc = ptDocumentRepository.findByIdAndPtProfile_Id(documentId, ptId)
                .orElseThrow(() -> new ResourceNotFoundException("PT document", documentId));
        ptDocumentRepository.delete(doc);
        log.info("Document {} of PT {} deleted by gym {}", documentId, ptId, gymUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtDocumentResponse> listDocuments(String gymUsername, Long ptId) {
        requireOwnedPt(gymUsername, ptId);
        return ptDocumentRepository.findByPtProfile_Id(ptId).stream()
                .map(PtDocumentResponse::of).toList();
    }

    /** PT phải thuộc Gym của operator đang đăng nhập (chống IDOR). */
    private PtProfile requireOwnedPt(String gymUsername, Long ptId) {
        return ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
    }
}
