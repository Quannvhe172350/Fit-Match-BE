package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.WithdrawalCreateRequest;
import com.fitmatch.dto.payment.WithdrawalResponse;
import com.fitmatch.entity.BankAccount;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WithdrawalRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BankAccountRepository;
import com.fitmatch.repository.WithdrawalRequestRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.WithdrawalService;
import com.fitmatch.service.support.PayoutQrService;
import com.fitmatch.service.support.WalletOwnerResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final BankAccountRepository bankAccountRepository;
    private final WalletService walletService;
    private final WalletOwnerResolver walletOwnerResolver;
    private final PayoutQrService payoutQrService;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    /** E-15: chủ ví — người cần biết kết quả xử lý lệnh rút. */
    private com.fitmatch.entity.User ownerOf(WithdrawalRequest request) {
        return request.getWallet().ownerUser();
    }

    /** Trang FE để user xem lệnh rút của mình, khác nhau theo vai trò. */
    private String ownerLinkOf(WithdrawalRequest request) {
        return switch (request.getWallet().getOwnerType()) {
            case GYM -> "/gym/wallet";
            case CUSTOMER -> "/profile/wallet";
        };
    }

    @Override
    @Transactional
    public WithdrawalResponse create(String username, WalletOwnerType ownerType,
                                     WithdrawalCreateRequest request) {
        // VND không có đơn vị lẻ, và QR VietQR chỉ nhận số nguyên đồng. Cho phép
        // rút số lẻ thì số tiền trên QR sẽ khác số đã giữ chỗ, và giao dịch CHI
        // trên sao kê không bao giờ khớp được -> lệnh kẹt ở APPROVED vĩnh viễn.
        BigDecimal amount = request.getAmount();
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Withdrawal amount must be a whole number of VND");
        }

        Wallet wallet = walletOwnerResolver.resolve(username, ownerType);
        BankAccount account = bankAccountRepository.findByIdAndUser_Username(request.getBankAccountId(), username)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", request.getBankAccountId()));

        // Giữ chỗ ngay khi tạo yêu cầu (available -> frozen) — chống rút trùng (UC-062).
        walletService.reserveForWithdrawal(wallet, amount);

        WithdrawalRequest saved = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .wallet(wallet)
                .amount(amount)
                // Snapshot thông tin ngân hàng: user sửa/xoá tài khoản sau đó
                // không được làm sai lệnh đã gửi cho admin chuyển khoản.
                .bankAccount(account.getAccountNumber())
                .bankName(account.getBank().getShortName())
                .bankBin(account.getBank().getBin())
                .accountHolder(account.getAccountHolder())
                .status(WithdrawalStatus.PENDING)
                .build());

        // refCode cần id nên chỉ sinh được sau khi save; phần hex tránh việc đoán
        // mã của lệnh khác chỉ từ số thứ tự.
        saved.setRefCode("FMW" + saved.getId()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        withdrawalRequestRepository.save(saved);

        auditService.record(AuditActions.WITHDRAWAL_REQUEST, "WithdrawalRequest", saved.getId(),
                ownerType + " " + username + " requested withdrawal " + amount);
        log.info("Withdrawal {} ({}) requested by {} ({}, ref={})",
                saved.getId(), ownerType, username, amount, saved.getRefCode());
        return WithdrawalResponse.of(saved);
    }

    @Override
    // KHÔNG readOnly: lần đầu chủ ví mở trang, resolve() còn phải TẠO ví. Trong
    // transaction read-only, Hibernate chuyển sang flush thủ công và bản ghi ví
    // mới có thể không được ghi xuống.
    @Transactional
    public PageResponse<WithdrawalResponse> listForOwner(String username, WalletOwnerType ownerType,
                                                          Pageable pageable) {
        Wallet wallet = walletOwnerResolver.resolve(username, ownerType);
        return PageResponse.of(
                withdrawalRequestRepository.findByWallet_IdOrderByIdDesc(wallet.getId(), pageable),
                WithdrawalResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> listForAdmin(WithdrawalStatus status, WalletOwnerType ownerType,
                                                          Pageable pageable) {
        // Bug S2-07 (cùng lỗi đã sửa ở RefundServiceImpl): không truyền bộ lọc
        // = KHÔNG lọc. Trước đây status null bị ép về PENDING nên "Tất cả trạng
        // thái" ở /admin/withdrawals trả đúng tập PENDING — admin tưởng filter hỏng.
        if (status == null && ownerType == null) {
            return PageResponse.of(withdrawalRequestRepository.findAll(pageable), WithdrawalResponse::of);
        }
        if (ownerType == null) {
            return PageResponse.of(withdrawalRequestRepository.findByStatus(status, pageable),
                    WithdrawalResponse::of);
        }
        if (status == null) {
            return PageResponse.of(withdrawalRequestRepository.findByWallet_OwnerType(ownerType, pageable),
                    WithdrawalResponse::of);
        }
        return PageResponse.of(
                withdrawalRequestRepository.findByStatusAndWallet_OwnerType(status, ownerType, pageable),
                WithdrawalResponse::of);
    }

    @Override
    @Transactional
    public WithdrawalResponse approve(Long id, String note, String actorUsername) {
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.PENDING);
        request.setStatus(WithdrawalStatus.APPROVED);
        request.setReviewNote(note);
        // QR sinh lúc duyệt chứ không lúc tạo: chỉ lệnh đã duyệt mới được chuyển
        // tiền, và admin không nên thấy mã quét được trên lệnh chưa xét duyệt.
        request.setQrContent(payoutQrService.buildQrUrl(request));
        withdrawalRequestRepository.save(request);
        auditService.record(AuditActions.WITHDRAWAL_APPROVE, "WithdrawalRequest", id,
                "Approved by " + actorUsername);
        // E-15 (audit 2026-07-17): trước đây chủ ví không được báo kết quả lệnh rút.
        notificationDispatcher.withdrawalDecided(ownerOf(request), id, "đã được duyệt",
                "Lệnh rút " + request.getAmount() + " đ đã được duyệt — chờ chuyển khoản.",
                ownerLinkOf(request));
        return WithdrawalResponse.of(request);
    }

    @Override
    @Transactional
    public WithdrawalResponse reject(Long id, String note, String actorUsername) {
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.PENDING);
        walletService.cancelWithdrawalReserve(request.getWallet(), request.getAmount());
        request.setStatus(WithdrawalStatus.REJECTED);
        request.setReviewNote(note);
        withdrawalRequestRepository.save(request);
        auditService.record(AuditActions.WITHDRAWAL_REJECT, "WithdrawalRequest", id,
                "Rejected by " + actorUsername + (note != null ? ": " + note : ""));
        notificationDispatcher.withdrawalDecided(ownerOf(request), id, "bị từ chối",
                "Lệnh rút " + request.getAmount() + " đ bị từ chối"
                        + (note != null && !note.isBlank() ? ": " + note.strip() + "." : ".")
                        + " Số tiền đã trở lại khả dụng.",
                ownerLinkOf(request));
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
        applyPayout(request, payoutReference.trim(), note, false);
        auditService.record(AuditActions.WITHDRAWAL_PAID, "WithdrawalRequest", id,
                "Paid out by " + actorUsername + " (" + request.getAmount()
                        + ", ref=" + payoutReference.trim() + ")");
        log.info("Withdrawal {} paid out ({}, ref={})", id, request.getAmount(), payoutReference.trim());
        return WithdrawalResponse.of(request);
    }

    @Override
    @Transactional
    public WithdrawalResponse markPaidByReconciliation(Long id, String cassoTxnId) {
        WithdrawalRequest request = requireStatus(id, WithdrawalStatus.APPROVED);
        applyPayout(request, "CASSO-" + cassoTxnId,
                "Tự động xác nhận từ biến động số dư Casso", true);
        auditService.record(AuditActions.WITHDRAWAL_PAID, "WithdrawalRequest", id,
                "Auto-matched from Casso statement (" + request.getAmount() + ", txn=" + cassoTxnId + ")");
        log.info("Withdrawal {} auto-marked PAID from Casso txn {} ({})",
                id, cassoTxnId, request.getAmount());
        return WithdrawalResponse.of(request);
    }

    /** Phần chung của mark-paid thủ công và tự động: trừ frozen, chốt trạng thái, báo chủ ví. */
    private void applyPayout(WithdrawalRequest request, String payoutReference, String note, boolean auto) {
        walletService.payoutWithdrawal(request.getWallet(), request.getAmount());
        request.setStatus(WithdrawalStatus.PAID);
        request.setPayoutReference(payoutReference);
        request.setPaidAt(LocalDateTime.now());
        request.setAutoMatched(auto);
        if (note != null) {
            request.setReviewNote(note);
        }
        withdrawalRequestRepository.save(request);
        notificationDispatcher.withdrawalDecided(ownerOf(request), request.getId(), "đã chi trả",
                "Đã chuyển khoản " + request.getAmount() + " đ (mã GD: " + payoutReference + ").",
                ownerLinkOf(request));
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
