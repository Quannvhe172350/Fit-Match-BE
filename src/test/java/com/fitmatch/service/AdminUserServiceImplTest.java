package com.fitmatch.service;

import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private AdminUserServiceImpl service;

    @Test
    void getUserDetail_found_returnsResponse() {
        User user = User.builder().id(5L).username("jane").email("jane@x.com").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserResponse res = service.getUserDetail(5L);

        assertThat(res.getUsername()).isEqualTo("jane");
    }

    @Test
    void getUserDetail_missing_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserDetail(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUserStatus_lock_persistsAndAudits() {
        User user = User.builder().id(2L).username("bob").status(UserStatus.ACTIVE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateUserStatus(2L, UserStatus.BANNED, "admin");

        assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
        org.mockito.Mockito.verify(auditService)
                .record(org.mockito.ArgumentMatchers.eq("USER_LOCK"),
                        org.mockito.ArgumentMatchers.eq("User"),
                        org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateUserStatus_onSelf_throws() {
        User admin = User.builder().id(1L).username("admin").status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserStatus(1L, UserStatus.BANNED, "admin"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assignRole_changesRoleAndAudits() {
        User user = User.builder().id(3L).username("carl")
                .role(com.fitmatch.common.enums.Role.ROLE_CUSTOMER).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.assignRole(3L, com.fitmatch.common.enums.Role.ROLE_ADMIN, "admin");

        assertThat(user.getRole()).isEqualTo(com.fitmatch.common.enums.Role.ROLE_ADMIN);
        org.mockito.Mockito.verify(auditService)
                .record(org.mockito.ArgumentMatchers.eq("USER_ROLE_ASSIGN"),
                        org.mockito.ArgumentMatchers.eq("User"),
                        org.mockito.ArgumentMatchers.eq(3L),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void assignRole_providerRole_throws() {
        // P1-1.4: không gán trực tiếp ROLE_GYM_OPERATOR/ROLE_PT (phải qua luồng đăng ký tạo profile).
        User user = User.builder().id(3L).username("carl")
                .role(com.fitmatch.common.enums.Role.ROLE_CUSTOMER).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.assignRole(3L, com.fitmatch.common.enums.Role.ROLE_GYM_OPERATOR, "admin"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.assignRole(3L, com.fitmatch.common.enums.Role.ROLE_PT, "admin"))
                .isInstanceOf(BusinessException.class);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(user);
    }

    @Test
    void assignRole_onSelf_throws() {
        User admin = User.builder().id(1L).username("admin")
                .role(com.fitmatch.common.enums.Role.ROLE_ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.assignRole(1L, com.fitmatch.common.enums.Role.ROLE_CUSTOMER, "admin"))
                .isInstanceOf(BusinessException.class);
    }
}
