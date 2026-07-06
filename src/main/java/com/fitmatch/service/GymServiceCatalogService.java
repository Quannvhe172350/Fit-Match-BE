package com.fitmatch.service;

import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;

import java.util.List;

/**
 * Quản lý danh mục dịch vụ của Gym (UC-53..55). Đặt tên "Catalog" để tránh nhầm với
 * lớp Spring service thông thường (entity là {@link com.fitmatch.entity.GymService}).
 */
public interface GymServiceCatalogService {

    /** UC-53: tạo dịch vụ Gym (yêu cầu Gym APPROVED). */
    GymServiceResponse create(String username, GymServiceRequest request);

    /** UC-54: cập nhật dịch vụ (chủ sở hữu). */
    GymServiceResponse update(String username, Long id, GymServiceRequest request);

    /** UC-54: vô hiệu hoá dịch vụ. */
    void deactivate(String username, Long id);

    /** UC-55: liệt kê dịch vụ của Gym mình. */
    List<GymServiceResponse> list(String username);

    /** UC-026: cấu hình quy tắc thanh toán/đặt lịch cho dịch vụ. */
    GymServiceResponse updateBookingRules(String username, Long id, com.fitmatch.dto.gym.BookingRulesDto rules);

    /** UC-027: đổi trạng thái vòng đời (PUBLISHED/HIDDEN/PAUSED/ARCHIVED); ARCHIVED là trạng thái cuối. */
    GymServiceResponse updateCatalogStatus(String username, Long id, com.fitmatch.common.enums.CatalogStatus status);
}
