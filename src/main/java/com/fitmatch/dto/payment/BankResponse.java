package com.fitmatch.dto.payment;

import com.fitmatch.entity.Bank;
import lombok.Builder;
import lombok.Getter;

/** Ngân hàng trong master data (V61) — FE dựng dropdown chọn ngân hàng. */
@Getter
@Builder
public class BankResponse {

    private Long id;
    /** Mã BIN 6 số chuẩn VietQR. */
    private String bin;
    private String code;
    private String shortName;
    private String name;

    public static BankResponse of(Bank b) {
        return BankResponse.builder()
                .id(b.getId())
                .bin(b.getBin())
                .code(b.getCode())
                .shortName(b.getShortName())
                .name(b.getName())
                .build();
    }
}
