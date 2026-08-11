package com.fitmatch.service.impl;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.media.MediaUpdateRequest;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.service.GymMediaService;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymMediaServiceImpl implements GymMediaService {

    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;
    private final MediaService mediaService;

    @Override
    @Transactional
    public List<GymMediaResponse> upload(String username, Long branchId, MediaImageType imageType,
                                         String caption, MultipartFile[] files) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        // Ownership của chi nhánh kiểm ở đây để trả 404 "branch không thuộc Gym"
        // đúng như hợp đồng cũ, thay vì 403 chung chung của MediaAccessGuard.
        if (branchId != null) {
            gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, username)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
        }
        MediaEntityType entityType = branchId != null ? MediaEntityType.BRANCH : MediaEntityType.GYM;
        Long entityId = branchId != null ? branchId : gym.getId();
        MediaImageType type = imageType != null ? imageType : MediaImageType.GALLERY;

        List<MediaResponse> uploaded = mediaService.upload(username, entityType, entityId, type, files);
        if (caption != null && !caption.isBlank()) {
            MediaUpdateRequest captionUpdate = new MediaUpdateRequest(caption, null, null);
            uploaded = uploaded.stream()
                    .map(m -> mediaService.update(username, m.getId(), captionUpdate))
                    .toList();
        }
        log.info("Gym {} uploaded {} image(s) for {} {}", username, uploaded.size(), entityType, entityId);
        return uploaded.stream().map(GymMediaResponse::of).toList();
    }

    @Override
    @Transactional
    public void delete(String username, Long mediaId) {
        mediaService.delete(username, mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymMediaResponse> list(String username, Long branchId) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        if (branchId != null) {
            gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, username)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
            return mediaService.list(username, MediaEntityType.BRANCH, branchId, null)
                    .stream().map(GymMediaResponse::of).toList();
        }
        // Không lọc chi nhánh: trả ảnh chung của Gym + ảnh của mọi chi nhánh, đúng
        // hành vi cũ (gym_media.findByGymProfile_Id trả về cả hai).
        List<GymMediaResponse> all = new ArrayList<>(
                mediaService.list(username, MediaEntityType.GYM, gym.getId(), null)
                        .stream().map(GymMediaResponse::of).toList());
        List<Long> branchIds = gymBranchRepository.findByGymProfile_Id(gym.getId())
                .stream().map(b -> b.getId()).toList();
        mediaService.listForEntities(MediaEntityType.BRANCH, branchIds, null)
                .values().forEach(list -> list.forEach(m -> all.add(GymMediaResponse.of(m))));
        return all;
    }
}
