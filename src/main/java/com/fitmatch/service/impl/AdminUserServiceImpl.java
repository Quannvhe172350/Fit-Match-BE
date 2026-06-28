package com.fitmatch.service.impl;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.mapper.UserMapper;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.spec.UserSpecifications;
import com.fitmatch.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(String keyword, Role role, UserStatus status, Pageable pageable) {
        // where()/and() tolerate null specifications -> filter nào null sẽ bị bỏ qua.
        Specification<User> spec = Specification.where(UserSpecifications.keyword(keyword))
                .and(UserSpecifications.hasRole(role))
                .and(UserSpecifications.hasStatus(status));
        return PageResponse.of(userRepository.findAll(spec, pageable), UserMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return UserMapper.toResponse(user);
    }
}
