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
        gym.setGeocodedAt(LocalDateTime.now());
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
        branch.setGeocodedAt(LocalDateTime.now());
        gymBranchRepository.save(branch);
        return true;
    }
}
