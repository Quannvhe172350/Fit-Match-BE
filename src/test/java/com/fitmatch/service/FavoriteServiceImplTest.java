package com.fitmatch.service;

import com.fitmatch.common.enums.FavoriteType;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.Favorite;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.FavoriteRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.FavoriteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private GymProfileRepository gymProfileRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private FavoriteServiceImpl service;

    @Test
    void addPtFavorite_persists() {
        when(ptProfileRepository.findByIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.APPROVED))
                .thenReturn(Optional.of(PtProfile.builder().id(1L).build()));
        when(favoriteRepository.existsByUser_UsernameAndTypeAndTargetId("u", FavoriteType.PT, 1L)).thenReturn(false);
        when(userRepository.findByUsername("u")).thenReturn(Optional.of(User.builder().username("u").build()));

        service.addPtFavorite("u", 1L);

        verify(favoriteRepository).save(any(Favorite.class));
    }

    @Test
    void addPtFavorite_duplicate_throws() {
        when(ptProfileRepository.findByIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.APPROVED))
                .thenReturn(Optional.of(PtProfile.builder().id(1L).build()));
        when(favoriteRepository.existsByUser_UsernameAndTypeAndTargetId("u", FavoriteType.PT, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.addPtFavorite("u", 1L)).isInstanceOf(BusinessException.class);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void removePtFavorite_notFound_throws() {
        when(favoriteRepository.deleteByUser_UsernameAndTypeAndTargetId("u", FavoriteType.PT, 9L)).thenReturn(0L);
        assertThatThrownBy(() -> service.removePtFavorite("u", 9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
