package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.payment.BankAccountRequest;
import com.fitmatch.dto.payment.BankAccountResponse;
import com.fitmatch.dto.payment.BankResponse;
import com.fitmatch.entity.Bank;
import com.fitmatch.entity.BankAccount;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BankAccountRepository;
import com.fitmatch.repository.BankRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    /** Chặn tạo tài khoản vô hạn — mỗi user thực tế chỉ dùng vài cái. */
    private static final long MAX_ACCOUNTS_PER_USER = 10;

    private final BankRepository bankRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BankResponse> listBanks() {
        return bankRepository.findByActiveTrueOrderByShortNameAsc().stream()
                .map(BankResponse::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankAccountResponse> listMine(String username) {
        return bankAccountRepository.findByUser_UsernameOrderByDefaultAccountDescIdAsc(username).stream()
                .map(BankAccountResponse::of)
                .toList();
    }

    @Override
    @Transactional
    public BankAccountResponse create(String username, BankAccountRequest request) {
        if (bankAccountRepository.countByUser_Username(username) >= MAX_ACCOUNTS_PER_USER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Mỗi tài khoản chỉ lưu tối đa " + MAX_ACCOUNTS_PER_USER + " tài khoản ngân hàng");
        }
        Bank bank = requireBank(request.getBankId());
        String accountNumber = request.getAccountNumber().trim();
        if (bankAccountRepository.existsByUser_UsernameAndBank_IdAndAccountNumber(
                username, bank.getId(), accountNumber)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tài khoản ngân hàng này đã được lưu trước đó");
        }
        // Tài khoản đầu tiên luôn là mặc định — nếu không, form rút tiền mở ra
        // không có gì được chọn sẵn dù user chỉ có đúng một tài khoản.
        boolean makeDefault = request.isSetDefault()
                || bankAccountRepository.countByUser_Username(username) == 0;

        BankAccount saved = bankAccountRepository.save(BankAccount.builder()
                .user(userRepository.findByUsername(username)
                        .orElseThrow(() -> new ResourceNotFoundException("User", username)))
                .bank(bank)
                .accountNumber(accountNumber)
                .accountHolder(request.getAccountHolder().trim())
                .defaultAccount(makeDefault)
                .build());
        if (makeDefault) {
            bankAccountRepository.clearDefaultExcept(username, saved.getId());
        }
        log.info("Bank account {} added for {} ({} - {})",
                saved.getId(), username, bank.getShortName(), accountNumber);
        return BankAccountResponse.of(saved);
    }

    @Override
    @Transactional
    public BankAccountResponse update(String username, Long id, BankAccountRequest request) {
        BankAccount account = requireOwned(username, id);
        Bank bank = requireBank(request.getBankId());
        account.setBank(bank);
        account.setAccountNumber(request.getAccountNumber().trim());
        account.setAccountHolder(request.getAccountHolder().trim());
        if (request.isSetDefault()) {
            account.setDefaultAccount(true);
            bankAccountRepository.clearDefaultExcept(username, account.getId());
        }
        return BankAccountResponse.of(bankAccountRepository.save(account));
    }

    @Override
    @Transactional
    public BankAccountResponse setDefault(String username, Long id) {
        BankAccount account = requireOwned(username, id);
        account.setDefaultAccount(true);
        bankAccountRepository.save(account);
        bankAccountRepository.clearDefaultExcept(username, account.getId());
        return BankAccountResponse.of(account);
    }

    @Override
    @Transactional
    public void delete(String username, Long id) {
        // Xoá an toàn kể cả khi đang có lệnh rút dở: withdrawal_requests giữ bản
        // sao số tài khoản/tên ngân hàng/chủ tài khoản chứ không tham chiếu bản
        // ghi này, nên admin vẫn chuyển khoản đúng chỗ.
        BankAccount account = requireOwned(username, id);
        boolean wasDefault = account.isDefaultAccount();
        bankAccountRepository.delete(account);
        if (wasDefault) {
            // Thăng tài khoản còn lại đầu tiên lên mặc định, tránh trạng thái
            // "có tài khoản nhưng không cái nào được chọn sẵn".
            bankAccountRepository.findByUser_UsernameOrderByDefaultAccountDescIdAsc(username).stream()
                    // Loại bản ghi vừa xoá phòng khi persistence context chưa flush kịp.
                    .filter(next -> !next.getId().equals(id))
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultAccount(true);
                        bankAccountRepository.save(next);
                    });
        }
        log.info("Bank account {} removed for {}", id, username);
    }

    private Bank requireBank(Long bankId) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank", bankId));
        if (!bank.isActive()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ngân hàng " + bank.getShortName() + " hiện không được hỗ trợ");
        }
        return bank;
    }

    private BankAccount requireOwned(String username, Long id) {
        return bankAccountRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", id));
    }
}
