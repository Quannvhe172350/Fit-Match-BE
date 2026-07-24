package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.mapper.UserMapper;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.spec.UserSpecifications;
import com.fitmatch.service.AdminUserService;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

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

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, UserStatus status, String actorUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        // Chặn admin tự khoá/đổi trạng thái chính tài khoản của mình.
        if (user.getUsername().equals(actorUsername)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "You cannot change your own account status");
        }
        if (user.getStatus() == status) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "User is already " + status);
        }

        UserStatus previous = user.getStatus();
        user.setStatus(status);
        user = userRepository.save(user);

        String action = status == UserStatus.BANNED ? AuditActions.USER_LOCK
                : (status == UserStatus.ACTIVE ? AuditActions.USER_UNLOCK : AuditActions.USER_STATUS_CHANGE);
        auditService.record(action, "User", id,
                String.format("Status %s -> %s by %s", previous, status, actorUsername));
        log.info("User {} status changed {} -> {} by {}", id, previous, status, actorUsername);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse assignRole(Long id, Role role, String actorUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        // Chặn admin tự thay đổi role của chính mình (tránh tự hạ quyền / khoá quản trị).
        if (user.getUsername().equals(actorUsername)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "You cannot change your own role");
        }
        // P1-1.4: ROLE_GYM_OPERATOR/ROLE_PT phải đi qua luồng đăng ký gym / tạo PT dưới
        // gym — các luồng đó tạo gym_profiles/pt_profiles tương ứng. Gán role trực tiếp ở
        // đây sẽ tạo user provider KHÔNG có profile -> 404 khi vào workspace, dữ liệu lệch.
        if (role == Role.ROLE_GYM_OPERATOR || role == Role.ROLE_PT) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot assign " + role + " directly; provider accounts are created via the "
                            + "gym registration / gym-managed PT flow so their profile is set up.");
        }
        if (user.getRole() == role) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "User already has role " + role);
        }

        Role previous = user.getRole();
        user.setRole(role);
        user = userRepository.save(user);

        auditService.record(AuditActions.USER_ROLE_ASSIGN, "User", id,
                String.format("Role %s -> %s by %s", previous, role, actorUsername));
        log.info("User {} role changed {} -> {} by {}", id, previous, role, actorUsername);
        return UserMapper.toResponse(user);
    }
}
