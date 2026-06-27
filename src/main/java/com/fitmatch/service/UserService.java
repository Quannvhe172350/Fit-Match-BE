package com.fitmatch.service;

import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserResponse getProfile(String username);

    UserResponse updateProfile(String username, UpdateProfileRequest request);

    UserResponse uploadAvatar(String username, MultipartFile file);
}
