package com.fitmatch.service;

import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;

public interface UserService {

    UserResponse getProfile(String username);

    UserResponse updateProfile(String username, UpdateProfileRequest request);
}
