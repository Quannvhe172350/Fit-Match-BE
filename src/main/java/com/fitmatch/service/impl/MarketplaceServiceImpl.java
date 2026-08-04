package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.gym.GymSearchCriteria;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.dto.gym.OperatingHourDto;
import com.fitmatch.dto.gym.TrainingPackageResponse;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymMediaRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.repository.projection.GymDistanceView;
import com.fitmatch.repository.spec.GymProfileSpecifications;
import com.fitmatch.repository.spec.PtProfileSpecifications;
import com.fitmatch.service.MarketplaceService;
import com.fitmatch.service.support.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {

    private final PtProfileRepository ptProfileRepository;
    private final com.fitmatch.repository.PtAssignmentRepository ptAssignmentRepository;
    private final PtCertificationRepository ptCertificationRepository;
    private final GymProfileRepository gymProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;
    private final GymMediaRepository gymMediaRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final com.fitmatch.repository.AvailabilitySlotRepository availabilitySlotRepository;
    private final com.fitmatch.service.support.RatingAggregator ratingAggregator;
    private final com.fitmatch.config.GoogleMapsProperties googleMapsProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtPublicProfileResponse> searchPts(String keyword, List<String> specializations,
                                                           String serviceArea, Pageable pageable) {
        Specification<PtProfile> spec = Specification.where(PtProfileSpecifications.visibleOnMarketplace())
                .and(PtProfileSpecifications.keyword(keyword))
                .and(PtProfileSpecifications.specializationIn(specializations))
                .and(PtProfileSpecifications.serviceArea(serviceArea));
        // Danh sách: không kèm chứng chỉ để tránh N+1; chứng chỉ chỉ trả ở detail.
        // UC-071: kèm điểm đánh giá để card hiển thị sao ngay trên danh sách.
        return PageResponse.of(ptProfileRepository.findAll(spec, pageable),
                p -> {
                    var rating = ratingAggregator.forPt(p.getId());
                    return PtPublicProfileResponse.of(p, List.of(), rating.average(), rating.count());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PtPublicProfileResponse getPtDetail(Long ptProfileId) {
        // UC-021: PT hiển thị khi ACTIVE và Gym chịu trách nhiệm APPROVED + đang hiển thị.
        PtProfile profile = ptProfileRepository
                .findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
                        ptProfileId, PtStatus.ACTIVE, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptProfileId));
        List<CertificationResponse> certs = ptCertificationRepository.findByPtProfile_Id(ptProfileId).stream()
                .map(CertificationResponse::of).toList();
        var rating = ratingAggregator.forPt(ptProfileId);
        return PtPublicProfileResponse.of(profile, certs, rating.average(), rating.count());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GymPublicProfileResponse> searchGyms(GymSearchCriteria criteria, Pageable pageable) {
        if (criteria.hasLocation()) {
            return searchGymsNearby(criteria, pageable);
        }
        Specification<GymProfile> spec = Specification.where(GymProfileSpecifications.visibleOnMarketplace())
                .and(GymProfileSpecifications.keyword(criteria.keyword()))
                .and(GymProfileSpecifications.city(criteria.city()))
                .and(GymProfileSpecifications.district(criteria.district()))
                .and(GymProfileSpecifications.packagePriceRange(criteria.minPrice(), criteria.maxPrice()));
        // UC-071: kèm điểm đánh giá để card hiển thị sao ngay trên danh sách.
        return PageResponse.of(gymProfileRepository.findAll(spec, pageable), this::toCardResponse);
    }

    /**
     * UC-18 (V55): nhánh tìm theo bán kính. Truy vấn native chỉ trả về (gymId,
     * distanceKm) đã sắp xếp; phần làm giàu dữ liệu (rating, ảnh bìa, điểm gần
     * nhất) làm ở đây theo lô để không sinh N+1.
     */
    private PageResponse<GymPublicProfileResponse> searchGymsNearby(GymSearchCriteria criteria, Pageable pageable) {
        double lat = criteria.latitude().doubleValue();
        double lng = criteria.longitude().doubleValue();
        double radiusKm = effectiveRadiusKm(criteria.radiusKm());
        double latDelta = GeoUtils.latDelta(radiusKm);
        double lngDelta = GeoUtils.lngDelta(lat, radiusKm);

        // Truy vấn đã ORDER BY khoảng cách; truyền Pageable KHÔNG sort để Spring
        // không nối thêm mệnh đề ORDER BY theo tên field entity (native query
        // không có alias đó -> lỗi SQL).
        Pageable byDistance = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<GymDistanceView> page = gymProfileRepository.searchNearby(
                lat, lng, radiusKm,
                // Bounding box chỉ để tận dụng index; haversine trong truy vấn mới là
                // bộ lọc chính xác. Kẹp biên vì toạ độ ngoài [-90,90]/[-180,180] là vô nghĩa
                // (không xử lý vắt qua kinh tuyến 180 — ngoài phạm vi thị trường Việt Nam).
                clamp(lat - latDelta, -90, 90), clamp(lat + latDelta, -90, 90),
                clamp(lng - lngDelta, -180, 180), clamp(lng + lngDelta, -180, 180),
                likePattern(criteria.keyword()), likePattern(criteria.city()), likePattern(criteria.district()),
                criteria.minPrice(), criteria.maxPrice(),
                byDistance);

        // ids rỗng -> hai truy vấn dưới đây là no-op và mapper không bao giờ chạy,
        // nên không cần nhánh đặc biệt cho trang trống.
        List<Long> ids = page.getContent().stream().map(GymDistanceView::getGymId).toList();
        Map<Long, GymProfile> gyms = gymProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(GymProfile::getId, g -> g));
        Map<Long, List<com.fitmatch.entity.GymBranch>> branchesByGym =
                gymBranchRepository.findByGymProfile_IdInAndActiveTrue(ids).stream()
                        .collect(Collectors.groupingBy(b -> b.getGymProfile().getId()));

        return PageResponse.of(page, view -> {
            GymProfile gym = gyms.get(view.getGymId());
            GymPublicProfileResponse response = toCardResponse(gym);
            NearestPoint nearest = nearestPoint(lat, lng, gym,
                    branchesByGym.getOrDefault(gym.getId(), List.of()));
            if (nearest != null) {
                // Marker phải trùng điểm sinh ra khoảng cách đang hiển thị, nếu không
                // người dùng thấy "cách 1.2km" nhưng ghim nằm ở trụ sở cách 8km.
                response.setLatitude(nearest.latitude());
                response.setLongitude(nearest.longitude());
                response.setNearestBranchName(nearest.branchName());
                response.setDistanceKm(round2(nearest.distanceKm()));
            } else if (view.getDistanceKm() != null) {
                response.setDistanceKm(round2(view.getDistanceKm()));
            }
            return response;
        });
    }

    /** Card marketplace: hồ sơ công khai + điểm đánh giá (UC-071) + ảnh bìa (bug 11). */
    private GymPublicProfileResponse toCardResponse(GymProfile gym) {
        var rating = ratingAggregator.forGym(gym.getId());
        var response = GymPublicProfileResponse.of(gym, rating.average(), rating.count());
        gymMediaRepository.findByGymProfile_Id(gym.getId()).stream()
                .findFirst()
                .ifPresent(m -> response.setCoverUrl(m.getUrl()));
        return response;
    }

    /** Điểm gần người dùng nhất trong số {trụ sở, các chi nhánh đang hoạt động}. */
    private NearestPoint nearestPoint(double lat, double lng, GymProfile gym,
                                      List<com.fitmatch.entity.GymBranch> branches) {
        NearestPoint best = null;
        if (gym.getLatitude() != null && gym.getLongitude() != null) {
            best = new NearestPoint(gym.getLatitude(), gym.getLongitude(), null,
                    GeoUtils.haversineKm(lat, lng,
                            gym.getLatitude().doubleValue(), gym.getLongitude().doubleValue()));
        }
        for (var branch : branches) {
            if (branch.getLatitude() == null || branch.getLongitude() == null) {
                continue;
            }
            double distance = GeoUtils.haversineKm(lat, lng,
                    branch.getLatitude().doubleValue(), branch.getLongitude().doubleValue());
            if (best == null || distance < best.distanceKm()) {
                best = new NearestPoint(branch.getLatitude(), branch.getLongitude(), branch.getName(), distance);
            }
        }
        return best;
    }

    private record NearestPoint(java.math.BigDecimal latitude, java.math.BigDecimal longitude,
                                String branchName, double distanceKm) {
    }

    /** Bán kính client gửi lên, kẹp vào [0, max] cấu hình; null -> mặc định. */
    private double effectiveRadiusKm(Double requested) {
        double max = googleMapsProperties.getMaxSearchRadiusKm();
        if (requested == null || requested <= 0) {
            return Math.min(googleMapsProperties.getDefaultSearchRadiusKm(), max);
        }
        return Math.min(requested, max);
    }

    /** Chuẩn hoá thành pattern LIKE viết thường; null/rỗng -> null = không lọc. */
    private static String likePattern(String value) {
        return org.springframework.util.StringUtils.hasText(value)
                ? "%" + value.trim().toLowerCase() + "%"
                : null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Khoảng cách hiển thị chỉ cần độ chính xác ~10m; tránh trả 1.2999999999 ra JSON. */
    private static double round2(double km) {
        return Math.round(km * 100.0) / 100.0;
    }

    @Override
    @Transactional(readOnly = true)
    public GymPublicProfileResponse getGymDetail(Long gymProfileId) {
        var gym = requireVisibleGym(gymProfileId);
        var rating = ratingAggregator.forGym(gymProfileId);
        return GymPublicProfileResponse.of(gym, rating.average(), rating.count());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> listGymBranches(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymBranchRepository.findByGymProfile_IdAndActiveTrue(gymProfileId).stream()
                .map(b -> BranchResponse.of(b,
                        operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(b.getId()).stream()
                                .map(OperatingHourDto::of).toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymServiceResponse> listGymServices(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymServiceRepository.findByGymProfile_IdAndStatus(gymProfileId, CatalogStatus.PUBLISHED)
                .stream().map(GymServiceResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingPackageResponse> listGymPackages(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return trainingPackageRepository.findByGymProfile_IdAndStatus(gymProfileId, CatalogStatus.PUBLISHED)
                .stream().map(TrainingPackageResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymMediaResponse> listGymMedia(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymMediaRepository.findByGymProfile_Id(gymProfileId)
                .stream().map(GymMediaResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtPublicProfileResponse> listGymPts(Long gymProfileId, Long branchId, Pageable pageable) {
        requireVisibleGym(gymProfileId);
        // Danh sách: không kèm chứng chỉ (tránh N+1); chứng chỉ trả ở PT detail.
        // UC-071: kèm điểm đánh giá để card PT của gym hiển thị sao.
        Page<PtProfile> page;
        if (branchId != null) {
            // Bug S2-04: PT chỉ phụ trách một chi nhánh -> chọn sai chi nhánh thì
            // checkout mới báo "PT chưa được gán cho ... chi nhánh đã chọn". Lọc ngay
            // ở đây để khách chỉ thấy PT thật sự phục vụ chi nhánh đang chọn.
            List<Long> ptIds = ptAssignmentRepository.findPtIdsByBranchId(branchId);
            page = ptIds.isEmpty()
                    ? Page.empty(pageable)
                    : ptProfileRepository.findByGymProfile_IdAndStatusAndIdIn(
                            gymProfileId, PtStatus.ACTIVE, ptIds, pageable);
        } else {
            page = ptProfileRepository.findByGymProfile_IdAndStatus(gymProfileId, PtStatus.ACTIVE, pageable);
        }
        return PageResponse.of(page, p -> {
            var rating = ratingAggregator.forPt(p.getId());
            return PtPublicProfileResponse.of(p, List.of(), rating.average(), rating.count());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.fitmatch.dto.pt.AvailabilitySlotDto> listPtAvailability(Long ptProfileId) {
        // Cùng điều kiện hiển thị với getPtDetail — PT ẩn thì lịch cũng không lộ.
        ptProfileRepository
                .findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
                        ptProfileId, PtStatus.ACTIVE, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptProfileId));
        return availabilitySlotRepository
                .findByPtProfile_IdOrderByDayOfWeekAscStartTimeAsc(ptProfileId).stream()
                .map(com.fitmatch.dto.pt.AvailabilitySlotDto::of).toList();
    }

    /** Gym chỉ public khi APPROVED + đang hiển thị (UC-018) — 404 nếu không. */
    private GymProfile requireVisibleGym(Long gymProfileId) {
        return gymProfileRepository
                .findByIdAndVerificationStatusAndActiveTrue(gymProfileId, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile", gymProfileId));
    }
}
