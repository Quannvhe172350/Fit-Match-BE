package com.fitmatch.service;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.user.UserResponse;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    /** UC-10: tìm kiếm/lọc danh sách user (keyword, role, status) có phân trang. */
    PageResponse<UserResponse> searchUsers(String keyword, Role role, UserStatus status, Pageable pageable);

    /** UC-10: xem chi tiết một user theo id. */
    UserResponse getUserDetail(Long id);
}
