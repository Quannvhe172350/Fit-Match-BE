package com.fitmatch.repository;

import com.fitmatch.common.enums.FavoriteType;
import com.fitmatch.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUser_UsernameAndTypeAndTargetId(String username, FavoriteType type, Long targetId);

    long deleteByUser_UsernameAndTypeAndTargetId(String username, FavoriteType type, Long targetId);

    List<Favorite> findByUser_UsernameAndType(String username, FavoriteType type);
}
