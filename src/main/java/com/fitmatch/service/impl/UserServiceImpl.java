package com.fitmatch.service.impl;

import com.fitmatch.dto.user.EmergencyContactDto;
import com.fitmatch.dto.user.FitnessPreferencesDto;
import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.mapper.UserMapper;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.StorageService;
import com.fitmatch.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    public UserResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getLocation() != null) user.setLocation(request.getLocation());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getHeight() != null) user.setHeight(request.getHeight());
        if (request.getWeight() != null) user.setWeight(request.getWeight());
        if (request.getMainGoal() != null) user.setMainGoal(request.getMainGoal());

        EmergencyContactDto ec = request.getEmergencyContact();
        if (ec != null) {
            if (ec.getName() != null) user.setEmergencyContactName(ec.getName());
            if (ec.getRelationship() != null) user.setEmergencyContactRelationship(ec.getRelationship());
            if (ec.getPhone() != null) user.setEmergencyContactPhone(ec.getPhone());
        }

        FitnessPreferencesDto fp = request.getFitnessPreferences();
        if (fp != null) {
            if (fp.getStyles() != null) user.setFitnessStyles(UserMapper.joinStyles(fp.getStyles()));
            if (fp.getFrequency() != null) user.setFitnessFrequency(fp.getFrequency());
            if (fp.getEquipmentAccess() != null) user.setFitnessEquipmentAccess(fp.getEquipmentAccess());
            if (fp.getInjuries() != null) user.setFitnessInjuries(fp.getInjuries());
        }

        user = userRepository.save(user);
        log.info("Profile updated for user: {}", username);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse uploadAvatar(String username, MultipartFile file) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : ".jpg";
        String filename = UUID.randomUUID() + ext;

        // Xoá avatar cũ trên GCS nếu có
        if (user.getAvatarUrl() != null) {
            storageService.delete(user.getAvatarUrl());
        }

        // Lưu public URL thẳng vào DB
        String publicUrl = storageService.upload("avatars", filename, file);
        user.setAvatarUrl(publicUrl);
        user = userRepository.save(user);

        log.info("Avatar uploaded for user: {}", username);
        return UserMapper.toResponse(user);
    }
}
