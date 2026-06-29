package com.fitmatch.service.impl;

import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.entity.PtCertification;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.PtCertificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtCertificationServiceImpl implements PtCertificationService {

    private final PtCertificationRepository certificationRepository;
    private final PtProfileRepository ptProfileRepository;

    @Override
    @Transactional
    public CertificationResponse add(String username, CertificationRequest request) {
        PtProfile profile = ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
        PtCertification cert = PtCertification.builder()
                .ptProfile(profile)
                .name(request.getName())
                .issuingOrganization(request.getIssuingOrganization())
                .issueDate(request.getIssueDate())
                .expiryDate(request.getExpiryDate())
                .credentialUrl(request.getCredentialUrl())
                .build();
        cert = certificationRepository.save(cert);
        log.info("PT {} added certification {}", username, cert.getId());
        return CertificationResponse.of(cert);
    }

    @Override
    @Transactional
    public CertificationResponse update(String username, Long certificationId, CertificationRequest request) {
        PtCertification cert = requireOwned(username, certificationId);
        cert.setName(request.getName());
        cert.setIssuingOrganization(request.getIssuingOrganization());
        cert.setIssueDate(request.getIssueDate());
        cert.setExpiryDate(request.getExpiryDate());
        cert.setCredentialUrl(request.getCredentialUrl());
        return CertificationResponse.of(certificationRepository.save(cert));
    }

    @Override
    @Transactional
    public void delete(String username, Long certificationId) {
        PtCertification cert = requireOwned(username, certificationId);
        certificationRepository.delete(cert);
        log.info("PT {} deleted certification {}", username, certificationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificationResponse> list(String username) {
        PtProfile profile = ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
        return certificationRepository.findByPtProfile_Id(profile.getId()).stream()
                .map(CertificationResponse::of).toList();
    }

    /** Định vị chứng chỉ và đảm bảo thuộc về user (chống IDOR). */
    private PtCertification requireOwned(String username, Long certificationId) {
        return certificationRepository.findByIdAndPtProfile_User_Username(certificationId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Certification", certificationId));
    }
}
