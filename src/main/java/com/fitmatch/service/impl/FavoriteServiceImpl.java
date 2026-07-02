package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.FavoriteType;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.entity.Favorite;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.FavoriteRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymProfileRepository gymProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void addPtFavorite(String username, Long ptProfileId) {
        ptProfileRepository.findByIdAndVerificationStatusAndActiveTrue(ptProfileId, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptProfileId));
        addFavorite(username, FavoriteType.PT, ptProfileId);
    }

    @Override
    @Transactional
    public void removePtFavorite(String username, Long ptProfileId) {
        removeFavorite(username, FavoriteType.PT, ptProfileId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtPublicProfileResponse> listPtFavorites(String username) {
        return favoriteRepository.findByUser_UsernameAndType(username, FavoriteType.PT).stream()
                .map(f -> ptProfileRepository.findById(f.getTargetId()).orElse(null))
                .filter(p -> p != null)
                .map(p -> PtPublicProfileResponse.of(p, List.of()))
                .toList();
    }

    @Override
    @Transactional
    public void addGymFavorite(String username, Long gymProfileId) {
        gymProfileRepository.findByIdAndVerificationStatusAndActiveTrue(gymProfileId, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile", gymProfileId));
        addFavorite(username, FavoriteType.GYM, gymProfileId);
    }

    @Override
    @Transactional
    public void removeGymFavorite(String username, Long gymProfileId) {
        removeFavorite(username, FavoriteType.GYM, gymProfileId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymPublicProfileResponse> listGymFavorites(String username) {
        return favoriteRepository.findByUser_UsernameAndType(username, FavoriteType.GYM).stream()
                .map(f -> gymProfileRepository.findById(f.getTargetId()).orElse(null))
                .filter(g -> g != null)
                .map(GymPublicProfileResponse::of)
                .toList();
    }

    private void addFavorite(String username, FavoriteType type, Long targetId) {
        if (favoriteRepository.existsByUser_UsernameAndTypeAndTargetId(username, type, targetId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Already in favorites");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        favoriteRepository.save(Favorite.builder().user(user).type(type).targetId(targetId).build());
        log.info("{} added {} #{} to favorites", username, type, targetId);
    }

    private void removeFavorite(String username, FavoriteType type, Long targetId) {
        long removed = favoriteRepository.deleteByUser_UsernameAndTypeAndTargetId(username, type, targetId);
        if (removed == 0) {
            throw new ResourceNotFoundException("Favorite", type + "#" + targetId);
        }
        log.info("{} removed {} #{} from favorites", username, type, targetId);
    }
}
