package com.fitmatch.service;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;

import java.util.List;

/**
 * Catalog dịch vụ kèm vé (V82). Dịch vụ là add-on tính tiền một lần khi mua vé,
 * KHÔNG phải đơn vị đặt lịch — vé vẫn là thứ duy nhất sinh buổi tập và escrow.
 */
public interface GymServiceCatalogService {

    GymServiceResponse create(String username, GymServiceRequest request);

    GymServiceResponse update(String username, Long id, GymServiceRequest request);

    /** Ẩn khỏi checkout; vé đã bán giữ nguyên dòng dịch vụ đã snapshot. */
    void deactivate(String username, Long id);

    List<GymServiceResponse> list(String username);

    GymServiceResponse updateCatalogStatus(String username, Long id, CatalogStatus status);

    /** Checkout: dịch vụ còn bán của gym sở hữu chi nhánh này. */
    List<GymServiceResponse> listForBranch(Long branchId);
}
