package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "C. Favorites", description = "Yêu thích PT & Gym (UC-15 → UC-21)")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "UC-15 — Thêm PT vào yêu thích", description = "Actor: **Customer**. Lỗi: 404 PT không hiển thị; 400 đã có trong yêu thích.")
    @PostMapping("/pts/{ptId}")
    public ResponseEntity<ApiResponse<Void>> addPt(@AuthenticationPrincipal UserDetails userDetails,
                                                   @PathVariable Long ptId) {
        favoriteService.addPtFavorite(userDetails.getUsername(), ptId);
        return ResponseEntity.ok(ApiResponse.success("PT added to favorites", null));
    }

    @Operation(summary = "UC-16 — Bỏ PT khỏi yêu thích", description = "Actor: **Customer**. Lỗi: 404 nếu không có trong yêu thích.")
    @DeleteMapping("/pts/{ptId}")
    public ResponseEntity<ApiResponse<Void>> removePt(@AuthenticationPrincipal UserDetails userDetails,
                                                      @PathVariable Long ptId) {
        favoriteService.removePtFavorite(userDetails.getUsername(), ptId);
        return ResponseEntity.ok(ApiResponse.success("PT removed from favorites", null));
    }
}
