package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.service.GeocodingBackfillService;
import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.support.AddressGeocoder;
import com.fitmatch.service.support.GeoPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingBackfillServiceImpl implements GeocodingBackfillService {

    private final GymProfileRepository gymProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GeocodingService geocodingService;
    private final com.fitmatch.repository.GeocodeCacheRepository geocodeCacheRepository;
    private final com.fitmatch.config.GoogleMapsProperties googleMapsProperties;

    @Override
    @Transactional
    public BackfillResult backfill(int limit, String actorUsername) {
        if (!geocodingService.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Geocoding is disabled: set app.google-maps.api-key first.");
        }
        List<GymProfile> gyms = gymProfileRepository.findByLatitudeIsNullAndAddressIsNotNull();
        List<GymBranch> branches = gymBranchRepository.findByLatitudeIsNullAndAddressIsNotNull();

        // Ngân sách chung cho cả hai loại: gym trước (trang chi tiết & card đều dùng
        // toạ độ trụ sở), chi nhánh dùng phần còn lại.
        int budget = Math.max(1, limit);
        List<GymProfile> gymBatch = gyms.stream().limit(budget).toList();
        int gymsUpdated = 0;
        for (GymProfile gym : gymBatch) {
            if (apply(gym)) {
                gymsUpdated++;
            }
        }
        List<GymBranch> branchBatch = branches.stream().limit(Math.max(0, budget - gymBatch.size())).toList();
        int branchesUpdated = 0;
        for (GymBranch branch : branchBatch) {
            if (apply(branch)) {
                branchesUpdated++;
            }
        }
        // "Còn lại" tính theo bản ghi CHƯA ĐỘNG TỚI cộng với bản ghi geocode thất bại
        // — địa chỉ rác sẽ được thử lại ở lần chạy sau, admin biết cần sửa tay.
        int remaining = (gyms.size() - gymsUpdated) + (branches.size() - branchesUpdated);
        log.info("Geocoding backfill by {}: gyms {}/{}, branches {}/{}, remaining {}",
                actorUsername, gymsUpdated, gymBatch.size(), branchesUpdated, branchBatch.size(), remaining);
        return new BackfillResult(gymBatch.size(), gymsUpdated, branchBatch.size(), branchesUpdated, remaining);
    }

    private boolean apply(GymProfile gym) {
        Optional<GeoPoint> point = geocodingService.geocode(
                AddressGeocoder.buildQuery(gym.getAddress(), gym.getDistrict(), gym.getCity()));
        if (point.isEmpty()) {
            return false;
        }
        GeoPoint p = point.get();
        gym.setLatitude(p.latitude());
        gym.setLongitude(p.longitude());
        gym.setPlaceId(p.placeId());
        gym.setFormattedAddress(p.formattedAddress());
        gym.setLocationType(p.locationType());
        gym.setGeocodedAt(LocalDateTime.now());
        // V59: backfill phải gắn cờ y hệt luồng operator tự lưu, nếu không hồ sơ
        // cũ có địa chỉ mơ hồ sẽ lặng lẽ vào kết quả tìm kiếm với ghim sai chỗ.
        com.fitmatch.service.support.AddressQuality.flagIfImprecise(gym, p.locationType());
        gymProfileRepository.save(gym);
        return true;
    }

    private boolean apply(GymBranch branch) {
        Optional<GeoPoint> point = geocodingService.geocode(
                AddressGeocoder.buildQuery(branch.getAddress(), branch.getDistrict(), branch.getCity()));
        if (point.isEmpty()) {
            return false;
        }
        GeoPoint p = point.get();
        branch.setLatitude(p.latitude());
        branch.setLongitude(p.longitude());
        branch.setPlaceId(p.placeId());
        branch.setFormattedAddress(p.formattedAddress());
        branch.setLocationType(p.locationType());
        branch.setGeocodedAt(LocalDateTime.now());
        gymBranchRepository.save(branch);
        return true;
    }

    @Override
    @Transactional
    public RefreshResult refreshStale(int limit) {
        int refreshAfterDays = googleMapsProperties.getRefreshAfterDays();
        if (!geocodingService.isEnabled() || refreshAfterDays <= 0) {
            return new RefreshResult(0, 0);
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(refreshAfterDays);
        int budget = Math.max(1, limit);

        List<GymProfile> gyms = gymProfileRepository
                .findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(cutoff)
                .stream().limit(budget).toList();
        int updated = 0;
        for (GymProfile gym : gyms) {
            if (refresh(gym)) {
                updated++;
            }
        }
        List<GymBranch> branches = gymBranchRepository
                .findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(cutoff).stream()
                .limit(Math.max(0, budget - gyms.size())).toList();
        for (GymBranch branch : branches) {
            if (refresh(branch)) {
                updated++;
            }
        }
        int scanned = gyms.size() + branches.size();
        if (scanned > 0) {
            log.info("Geocoding refresh: quét {} bản ghi cũ hơn {} ngày, {} bản ghi đổi toạ độ",
                    scanned, refreshAfterDays, updated);
        }
        return new RefreshResult(scanned, updated);
    }

    /**
     * Tra lại theo place_id. Google không trả kết quả (địa điểm bị gỡ khỏi bản đồ)
     * thì GIỮ NGUYÊN toạ độ cũ và vẫn dập lại {@code geocodedAt} — nếu không, bản
     * ghi đó bị lôi ra tra lại mỗi đêm cho tới hết đời.
     *
     * <p>Cố tình KHÔNG gọi {@code AddressQuality.flagIfImprecise} ở đây: job chạy
     * lúc 3 giờ sáng mà tự bắt một phòng gym đang hoạt động bình thường suốt nửa
     * năm phải đi xác minh lại địa chỉ là quá bất ngờ. Làm mới là chuyện toạ độ
     * trôi, việc chấm điểm địa chỉ thuộc về lúc operator lưu hoặc lúc backfill.
     */
    private boolean refresh(GymProfile gym) {
        Optional<GeoPoint> point = geocodingService.geocodeByPlaceId(gym.getPlaceId());
        gym.setGeocodedAt(LocalDateTime.now());
        boolean moved = point.isPresent() && hasMoved(gym.getLatitude(), gym.getLongitude(), point.get());
        point.ifPresent(p -> {
            gym.setLatitude(p.latitude());
            gym.setLongitude(p.longitude());
            gym.setFormattedAddress(p.formattedAddress());
            gym.setLocationType(p.locationType());
        });
        gymProfileRepository.save(gym);
        return moved;
    }

    private boolean refresh(GymBranch branch) {
        Optional<GeoPoint> point = geocodingService.geocodeByPlaceId(branch.getPlaceId());
        branch.setGeocodedAt(LocalDateTime.now());
        boolean moved = point.isPresent() && hasMoved(branch.getLatitude(), branch.getLongitude(), point.get());
        point.ifPresent(p -> {
            branch.setLatitude(p.latitude());
            branch.setLongitude(p.longitude());
            branch.setFormattedAddress(p.formattedAddress());
            branch.setLocationType(p.locationType());
        });
        gymBranchRepository.save(branch);
        return moved;
    }

    @Override
    @Transactional(readOnly = true)
    public GeocodingCoverage coverage() {
        return new GeocodingCoverage(
                gymProfileRepository.count(),
                gymProfileRepository.countByLatitudeIsNotNull(),
                gymProfileRepository.countByLocationType("APPROXIMATE"),
                gymProfileRepository.countByCoordinatesPinnedTrue(),
                gymBranchRepository.countByActiveTrue(),
                gymBranchRepository.countByLatitudeIsNotNullAndActiveTrue(),
                geocodeCacheRepository.count(),
                geocodingService.isEnabled());
    }

    /** So sánh theo giá trị số: BigDecimal.equals coi 21.03 khác 21.0300000. */
    private static boolean hasMoved(java.math.BigDecimal oldLat, java.math.BigDecimal oldLng, GeoPoint p) {
        if (oldLat == null || oldLng == null || p.latitude() == null || p.longitude() == null) {
            return true;
        }
        return oldLat.compareTo(p.latitude()) != 0 || oldLng.compareTo(p.longitude()) != 0;
    }
}
