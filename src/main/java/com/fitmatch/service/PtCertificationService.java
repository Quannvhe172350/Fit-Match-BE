package com.fitmatch.service;

import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;

import java.util.List;

public interface PtCertificationService {

    /** UC-27: thêm chứng chỉ cho hồ sơ PT của chính user. */
    CertificationResponse add(String username, CertificationRequest request);

    /** UC-27: cập nhật chứng chỉ (chỉ chủ sở hữu). */
    CertificationResponse update(String username, Long certificationId, CertificationRequest request);

    /** UC-27: xoá chứng chỉ (chỉ chủ sở hữu). */
    void delete(String username, Long certificationId);

    /** UC-27: liệt kê chứng chỉ của chính user. */
    List<CertificationResponse> list(String username);
}
