package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WithdrawalRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.WithdrawalRequestRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.WithdrawalService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletService walletService;
    private final GymProfileResolver gymProfileResolver;
    private final AuditService auditService;

    @Override
    @Transactional
    public WithdrawalResponse create(String gymUsername, WithdrawalCreateRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        Wallet wallet = walletService.getOrCreate(gym);

        // Giữ chỗ ngay khi tạo yêu cầu (available -> frozen) — chống rút trùng (UC-062).
        walletService.reserveForWithdrawal(gym.getId(), request.getAmount());

        WithdrawalRequest saved = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .bankAccount(request.getBankAccount())
                .bankName(request.getBankName())
                .accountHolder(request.getAccountHolder())
                .status(WithdrawalStatus.PENDING)
                .build());
        auditService.record(AuditActions.WITHDRAWAL_REQUEST, "WithdrawalRequest", saved.getId(),
                "Gym " + gymUsername + " requested withdrawal " + request.getAmount());
        log.info("Withdrawal {} requested by {} ({})", saved.getId(), gymUsername, request.getAmount());
        return WithdrawalResponse.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> listForGym(String gymUsername, Pageable pageable) {
        return PageResponse.of(
                withdrawalRequestRepository.findByWallet_GymProfile_User_Username(gymUsername, pageable),
                WithdrawalResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> listForAdmin(WithdrawalStatus status, Pageable pageable) {
        WithdrawalStatus effective = status != null ? status : WithdrawalStatus.PENDING;
        return PageResponse.of(withdrawalRequestRepository.findByStatus(effective, pageable),
                WithdrawalResponse::of);
    }

    @Override
    @Transactional
    public WithdrawalResponse approve(Long id, String note, String actorUsername) {
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.PENDING);
        request.setStatus(WithdrawalStatus.APPROVED);
        request.setReviewNote(note);
        withdrawalRequestRepository.save(request);
        auditService.record(AuditActions.WITHDRAWAL_APPROVE, "WithdrawalRequest", id,
                "Approved by " + actorUsername);
        return WithdrawalResponse.of(request);
    }

    @Override
    @Transactional
    public WithdrawalResponse reject(Long id, String note, String actorUsername) {
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.PENDING);
        walletService.cancelWithdrawalReserve(gymIdOf(request), request.getAmount());
        request.setStatus(WithdrawalStatus.REJECTED);
        request.setReviewNote(note);
        withdrawalRequestRepository.save(request);
        auditService.record(AuditActions.WITHDRAWAL_REJECT, "WithdrawalRequest", id,
                "Rejected by " + actorUsername + (note != null ? ": " + note : ""));
        return WithdrawalResponse.of(request);
    }

    @Override
    @Transactional
    public WithdrawalResponse markPaid(Long id, String payoutReference, String note, String actorUsername) {
        // D-11: mã giao dịch chuyển khoản bắt buộc — không có thì không đối soát được sao kê.
        if (payoutReference == null || payoutReference.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Payout reference is required");
        }
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.APPROVED);
        walletService.payoutWithdrawal(gymIdOf(request), request.getAmount());
        request.setStatus(WithdrawalStatus.PAID);
        request.setPayoutReference(payoutReference.trim());
        request.setReviewNote(note);
        withdrawalRequestRepository.save(request);
        auditService.record(AuditActions.WITHDRAWAL_PAID, "WithdrawalRequest", id,
                "Paid out by " + actorUsername + " (" + request.getAmount()
                        + ", ref=" + payoutReference.trim() + ")");
        log.info("Withdrawal {} paid out ({}, ref={})", id, request.getAmount(), payoutReference.trim());
        return WithdrawalResponse.of(request);
    }

    private Long gymIdOf(WithdrawalRequest request) {
        return request.getWallet().getGymProfile().getId();
    }

    private WithdrawalRequest requireStatus(Long id, WithdrawalStatus expected) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request", id));
        if (request.getStatus() != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Withdrawal request must be " + expected + " (current: " + request.getStatus() + ")");
        }
        return request;
    }
}
