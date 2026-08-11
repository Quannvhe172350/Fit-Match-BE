package com.fitmatch.service.impl;

import com.fitmatch.dto.user.EmergencyContactDto;
import com.fitmatch.dto.user.FitnessPreferencesDto;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.user.DeactivateAccountRequest;
import com.fitmatch.dto.user.UpdateProfileRequest;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.mapper.UserMapper;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.StorageService;
import com.fitmatch.service.UserService;
import com.fitmatch.service.support.EmailVerificationIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MediaService mediaService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationIssuer emailVerificationIssuer;

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

        // UC-002: đổi email = đổi danh tính liên hệ — phải là email chưa ai dùng
        // và phải xác minh lại quyền sở hữu (reset emailVerified + gửi token mới).
        boolean emailChanged = request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail());
        if (emailChanged) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS,
                        "Email '" + request.getEmail() + "' is already registered");
            }
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
        }

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
        if (emailChanged) {
            emailVerificationIssuer.issue(user);
            log.info("Email changed for user: {} — re-verification required", username);
        }
        log.info("Profile updated for user: {}", username);
        return UserMapper.toResponse(user);
    }

    /**
     * UC-05 (V64): avatar đi qua Media system thay vì tự upload — nhờ đó dùng chung
     * kiểm tra magic bytes, sinh thumbnail và dọn object khi rollback.
     *
     * <p>AVATAR là loại ảnh singleton: {@code MediaService.upload} tự xoá bản ghi +
     * object của avatar cũ, nên không tích tụ ảnh cũ trên bucket. Cột
     * {@code users.avatar_url} vẫn được cập nhật vì rất nhiều response (danh sách
     * booking, review, chat) đọc thẳng cột này — bỏ đi sẽ thành N+1 khắp nơi.
     */
    @Override
    @Transactional
    public UserResponse uploadAvatar(String username, MultipartFile file) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        String previousUrl = user.getAvatarUrl();
        List<MediaResponse> uploaded = mediaService.upload(username, MediaEntityType.USER, user.getId(),
                MediaImageType.AVATAR, new MultipartFile[]{file});

        user.setAvatarUrl(uploaded.get(0).getUrl());
        user = userRepository.save(user);

        // Avatar cũ từ trước V64 chỉ tồn tại dưới dạng URL trong users.avatar_url —
        // không có bản ghi media nào để MediaService dọn hộ, nên xoá tay ở đây.
        if (previousUrl != null && !previousUrl.equals(user.getAvatarUrl())) {
            try {
                storageService.delete(previousUrl);
            } catch (Exception e) {
                log.warn("Failed to delete old avatar {}: {}", previousUrl, e.getMessage());
            }
        }

        log.info("Avatar uploaded for user: {}", username);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void deactivateAccount(String username, DeactivateAccountRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        // Xác nhận mật khẩu trước khi vô hiệu hoá (bảo vệ thao tác nhạy cảm).
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Password is incorrect");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Account is already deactivated");
        }

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("Account deactivated by owner: {}", username);
    }
}
