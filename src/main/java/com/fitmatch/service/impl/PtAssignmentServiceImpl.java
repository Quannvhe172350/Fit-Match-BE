package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.dto.pt.PtAssignmentResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.PtAssignment;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.PtAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtAssignmentServiceImpl implements PtAssignmentService {

    private final PtAssignmentRepository ptAssignmentRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final TrainingSessionRepository trainingSessionRepository;

    @Override
    @Transactional
    public PtAssignmentResponse assign(String gymUsername, Long ptId, PtAssignmentRequest request) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);

        PtAssignment.PtAssignmentBuilder builder = PtAssignment.builder().ptProfile(pt).active(true);

        // Câu 24: đích duy nhất là chi nhánh. Dịch vụ/gói tập không còn tồn tại,
        // và PtSlotValidator chỉ hỏi "PT có phụ trách chi nhánh của vé không".
        if (request.getBranchId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "branchId is required");
        }
        if (ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(ptId, request.getBranchId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "PT is already assigned to this branch");
        }
        GymBranch branch = gymBranchRepository
                .findByIdAndGymProfile_User_Username(request.getBranchId(), gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
        // Chi nhánh đã ngừng hoạt động không nhận PT mới: gán vào đó thì PT không
        // thể phục vụ ai (marketplace chỉ trả chi nhánh active) mà nhìn danh sách
        // phân công lại tưởng PT đã có chỗ làm việc.
        if (!branch.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot assign PT to a deactivated branch #" + branch.getId()
                            + ". Reactivate it first.");
        }
        builder.gymBranch(branch);

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
        // PT phải luôn thuộc ít nhất một chi nhánh (UC-022): gỡ nốt chi nhánh cuối
        // thì PT không còn nơi làm việc — không hiện ở marketplace và mọi lượt chọn PT
        // đều bị PtSlotValidator từ chối, nhưng PT vẫn ACTIVE nên gym
        // không biết. Muốn PT ngừng làm thì tắt PT (UC-021), không phải gỡ hết.
        if (assignment.getGymBranch() != null
                && ptAssignmentRepository.countByPtProfile_Id(ptId) <= 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot remove the last branch of this PT. Assign another branch first, "
                            + "or deactivate the PT instead.");
        }
        // P1-16: không gỡ phân công khi PT còn buổi tập tương lai — nếu
        // không buổi tập sẽ trỏ tới PT không còn được gán (bỏ rơi khách). Gym phải
        // để khách đổi PT trước (đồng nhất với chặn ở deactivate PT).
        long future = trainingSessionRepository
                .countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
                        ptId, com.fitmatch.common.enums.SessionStatus.SCHEDULED, java.time.LocalDate.now());
        if (future > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể gỡ phân công: PT còn " + future + " buổi tập đã đặt. "
                            + "Hãy để khách đổi PT trước.");
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
