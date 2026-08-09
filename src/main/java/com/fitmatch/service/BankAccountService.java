package com.fitmatch.service;

import com.fitmatch.dto.payment.BankAccountRequest;
import com.fitmatch.dto.payment.BankAccountResponse;
import com.fitmatch.dto.payment.BankResponse;

import java.util.List;

/**
 * Tài khoản ngân hàng thụ hưởng của người dùng (V61) — nguồn dữ liệu cho lệnh
 * rút tiền và cho QR chuyển khoản mà admin quét.
 */
public interface BankAccountService {

    /** Master data ngân hàng đang hoạt động, kèm mã BIN. */
    List<BankResponse> listBanks();

    List<BankAccountResponse> listMine(String username);

    BankAccountResponse create(String username, BankAccountRequest request);

    BankAccountResponse update(String username, Long id, BankAccountRequest request);

    /** Đặt làm tài khoản mặc định; tài khoản mặc định cũ tự bị bỏ cờ. */
    BankAccountResponse setDefault(String username, Long id);

    void delete(String username, Long id);
}
