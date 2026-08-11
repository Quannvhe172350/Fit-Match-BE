package com.fitmatch.dto.payment;

import com.fitmatch.entity.BankAccount;
import lombok.Builder;
import lombok.Getter;

/** Tài khoản ngân hàng thụ hưởng đã lưu của user (V61). */
@Getter
@Builder
public class BankAccountResponse {

    private Long id;
    private Long bankId;
    private String bankBin;
    private String bankCode;
    private String bankName;
    private String accountNumber;
    private String accountHolder;
    private boolean defaultAccount;

    /** Chỉ gọi trong transaction — {@code bank} là lazy proxy. */
    public static BankAccountResponse of(BankAccount a) {
        return BankAccountResponse.builder()
                .id(a.getId())
                .bankId(a.getBank().getId())
                .bankBin(a.getBank().getBin())
                .bankCode(a.getBank().getCode())
                .bankName(a.getBank().getShortName())
                .accountNumber(a.getAccountNumber())
                .accountHolder(a.getAccountHolder())
                .defaultAccount(a.isDefaultAccount())
                .build();
    }
}
