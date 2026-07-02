package com.fitmatch.service;

import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;

import java.util.List;

public interface FavoriteService {

    /** UC-15: thêm PT vào yêu thích. */
    void addPtFavorite(String username, Long ptProfileId);

    /** UC-16: bỏ PT khỏi yêu thích. */
    void removePtFavorite(String username, Long ptProfileId);

    /** UC-17: danh sách PT yêu thích. */
    List<PtPublicProfileResponse> listPtFavorites(String username);

    /** UC-19: thêm Gym vào yêu thích. */
    void addGymFavorite(String username, Long gymProfileId);

    /** UC-20: bỏ Gym khỏi yêu thích. */
    void removeGymFavorite(String username, Long gymProfileId);

    /** UC-21: danh sách Gym yêu thích. */
    List<GymPublicProfileResponse> listGymFavorites(String username);
}
