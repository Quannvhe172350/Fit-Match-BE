package com.fitmatch.service;

import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
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
}
