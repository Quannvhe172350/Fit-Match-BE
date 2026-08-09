package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.dto.pt.PtAssignmentResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.PtAssignment;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.TrainingPackage;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.service.PtAssignmentService;
import com.fitmatch.service.support.BookingEligibilityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtAssignmentServiceImpl implements PtAssignmentService {

    private final PtAssignmentRepository ptAssignmentRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public PtAssignmentResponse assign(String gymUsername, Long ptId, PtAssignmentRequest request) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);

        long targets = Stream.of(request.getBranchId(), request.getServiceId(), request.getPackageId())
                .filter(Objects::nonNull).count();
        if (targets != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Exactly one of branchId, serviceId or packageId must be provided");
        }

        PtAssignment.PtAssignmentBuilder builder = PtAssignment.builder().ptProfile(pt).active(true);

        if (request.getBranchId() != null) {
            if (ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(ptId, request.getBranchId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "PT is already assigned to this branch");
            }
            GymBranch branch = gymBranchRepository
                    .findByIdAndGymProfile_User_Username(request.getBranchId(), gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
            // Chi nhánh đã ngừng thì không nhận booking nào (ScheduleConflictValidator
            // chặn ở khâu đặt lịch), nên phân công vào đó chỉ tạo bản ghi treo.
            if (!branch.isActive()) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Cannot assign PT to branch #" + branch.getId()
                                + ": the branch is deactivated. Reactivate it first.");
            }
            builder.gymBranch(branch);
        } else if (request.getServiceId() != null) {
            if (ptAssignmentRepository.existsByPtProfile_IdAndGymService_Id(ptId, request.getServiceId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "PT is already assigned to this service");
            }
            GymService service = gymServiceRepository
                    .findByIdAndGymProfile_User_Username(request.getServiceId(), gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym service", request.getServiceId()));
            builder.gymService(service);
        } else {
            if (ptAssignmentRepository.existsByPtProfile_IdAndTrainingPackage_Id(ptId, request.getPackageId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "PT is already assigned to this package");
            }
            TrainingPackage pkg = trainingPackageRepository
                    .findByIdAndGymProfile_User_Username(request.getPackageId(), gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Training package", request.getPackageId()));
            builder.trainingPackage(pkg);
        }

        PtAssignment saved = ptAssignmentRepository.save(builder.build());
        log.info("Gym {} assigned PT {} (assignment {})", gymUsername, ptId, saved.getId());
        return PtAssignmentResponse.of(saved);
    }

    @Override
    @Transactional
    public void remove(String gymUsername, Long ptId, Long assignmentId) {
        requireOwnedPt(gymUsername, ptId);
        PtAssignment assignment = ptAssignmentRepository
                .findByIdAndPtProfile_GymProfile_User_Username(assignmentId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT assignment", assignmentId));
        // P1-16: không gỡ phân công khi PT còn booking giữ chỗ tương lai — nếu
        // không booking sẽ trỏ tới PT không còn được gán (bỏ rơi). Gym phải
        // reassign/hủy các booking đó trước (đồng nhất với chặn ở deactivate PT).
        long future = bookingRepository.countByPtProfile_IdAndStatusInAndStartAtGreaterThan(
                ptId, BookingEligibilityChecker.HOLDING_STATUSES, LocalDateTime.now());
        if (future > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot remove assignment: PT has " + future + " upcoming booking(s). "
                            + "Reassign or cancel them first.");
        }
        ptAssignmentRepository.delete(assignment);
        log.info("Gym {} removed assignment {} of PT {}", gymUsername, assignmentId, ptId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtAssignmentResponse> list(String gymUsername, Long ptId) {
        requireOwnedPt(gymUsername, ptId);
        return ptAssignmentRepository.findByPtProfile_Id(ptId).stream()
                .map(PtAssignmentResponse::of).toList();
    }

    private PtProfile requireOwnedPt(String gymUsername, Long ptId) {
        PtProfile pt = ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        // P1-1.2: Gym bị đình chỉ / chưa duyệt không được quản lý phân công PT. null-safe.
        if (pt.getGymProfile() != null
                && pt.getGymProfile().getVerificationStatus() != com.fitmatch.common.enums.VerificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Gym must be APPROVED to manage its trainers (current: "
                            + pt.getGymProfile().getVerificationStatus() + ")");
        }
        return pt;
    }
}
