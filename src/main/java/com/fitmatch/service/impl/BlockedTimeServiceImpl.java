package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.dto.pt.BlockedTimeRequest;
import com.fitmatch.dto.pt.BlockedTimeResponse;
import com.fitmatch.entity.BlockedTime;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BlockedTimeRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.BlockedTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedTimeServiceImpl implements BlockedTimeService {

    private final BlockedTimeRepository blockedTimeRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final com.fitmatch.repository.BookingRepository bookingRepository;

    @Override
    @Transactional
    public BlockedTimeResponse createForGym(String gymUsername, BlockedTimeRequest request) {
        validateRange(request);
        boolean hasPt = request.getPtId() != null;
        boolean hasBranch = request.getBranchId() != null;
        if (hasPt == hasBranch) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Exactly one of ptId or branchId must be provided");
        }

        BlockedTime.BlockedTimeBuilder builder = BlockedTime.builder()
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .reason(request.getReason());

        if (hasPt) {
            PtProfile pt = ptProfileRepository
                    .findByIdAndGymProfile_User_Username(request.getPtId(), gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("PT profile", request.getPtId()));
            assertNoOverlappingPtBooking(pt.getId(), request);
            builder.ptProfile(pt);
        } else {
            GymBranch branch = gymBranchRepository
                    .findByIdAndGymProfile_User_Username(request.getBranchId(), gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
            assertNoOverlappingBranchBooking(branch.getId(), request);
            builder.gymBranch(branch);
        }

        BlockedTime saved = blockedTimeRepository.save(builder.build());
        log.info("Gym {} created blocked time {}", gymUsername, saved.getId());
        return BlockedTimeResponse.of(saved);
    }

    @Override
    @Transactional
    public BlockedTimeResponse createForPt(String ptUsername, BlockedTimeRequest request) {
        validateRange(request);
        PtProfile pt = requireOwnPt(ptUsername);
        if (pt.getStatus() == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "A suspended PT cannot manage blocked time");
        }
        assertNoOverlappingPtBooking(pt.getId(), request);
        BlockedTime saved = blockedTimeRepository.save(BlockedTime.builder()
                .ptProfile(pt)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .reason(request.getReason())
                .build());
        log.info("PT {} created blocked time {}", ptUsername, saved.getId());
        return BlockedTimeResponse.of(saved);
    }

    @Override
    @Transactional
    public void deleteForGym(String gymUsername, Long id) {
        BlockedTime blocked = blockedTimeRepository
                .findByIdAndPtProfile_GymProfile_User_Username(id, gymUsername)
                .or(() -> blockedTimeRepository.findByIdAndGymBranch_GymProfile_User_Username(id, gymUsername))
                .orElseThrow(() -> new ResourceNotFoundException("Blocked time", id));
        blockedTimeRepository.delete(blocked);
        log.info("Gym {} deleted blocked time {}", gymUsername, id);
    }

    @Override
    @Transactional
    public void deleteForPt(String ptUsername, Long id) {
        BlockedTime blocked = blockedTimeRepository.findByIdAndPtProfile_User_Username(id, ptUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Blocked time", id));
        blockedTimeRepository.delete(blocked);
        log.info("PT {} deleted blocked time {}", ptUsername, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> listForGym(String gymUsername, Long ptId, Long branchId) {
        if (ptId != null) {
            ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
            return blockedTimeRepository.findByPtProfile_IdOrderByStartAt(ptId).stream()
                    .map(BlockedTimeResponse::of).toList();
        }
        if (branchId != null) {
            gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
            return blockedTimeRepository.findByGymBranch_IdOrderByStartAt(branchId).stream()
                    .map(BlockedTimeResponse::of).toList();
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ptId or branchId is required");
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> listForPt(String ptUsername) {
        PtProfile pt = requireOwnPt(ptUsername);
        return blockedTimeRepository.findByPtProfile_IdOrderByStartAt(pt.getId()).stream()
                .map(BlockedTimeResponse::of).toList();
    }

    private void validateRange(BlockedTimeRequest request) {
        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startAt must be before endAt");
        }
    }

    // P1-15: không cho tạo khoảng chặn đè lên booking đang giữ chỗ (PENDING/CONFIRMED)
    // — nếu không sẽ có "PT/chi nhánh nghỉ nhưng booking vẫn CONFIRMED", khách đến
    // nơi đóng cửa. Gym phải dời/hủy các booking đó trước.
    private void assertNoOverlappingPtBooking(Long ptId, BlockedTimeRequest request) {
        boolean overlap = !bookingRepository
                .findByPtProfile_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                        ptId, com.fitmatch.service.support.BookingEligibilityChecker.HOLDING_STATUSES,
                        request.getEndAt(), request.getStartAt())
                .isEmpty();
        if (overlap) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot block this period: the PT has active bookings overlapping it "
                            + "(reschedule or cancel them first)");
        }
    }

    private void assertNoOverlappingBranchBooking(Long branchId, BlockedTimeRequest request) {
        long overlap = bookingRepository
                .countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                        branchId, com.fitmatch.service.support.BookingEligibilityChecker.HOLDING_STATUSES,
                        request.getEndAt(), request.getStartAt());
        if (overlap > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot block this period: the branch has active bookings overlapping it "
                            + "(reschedule or cancel them first)");
        }
    }

    private PtProfile requireOwnPt(String username) {
        return ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
    }
}
