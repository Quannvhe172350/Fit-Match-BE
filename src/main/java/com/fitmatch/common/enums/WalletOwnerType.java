package com.fitmatch.common.enums;

/**
 * Chủ sở hữu của một ví (V61). Một ví thuộc đúng một chủ; loại chủ quyết định
 * cột khoá ngoại nào được set trên {@code wallets} và luồng tiền nào ghi có.
 * <p>
 * PT không có ví: PT làm việc dưới quyền một phòng gym và được gym trả công
 * ngoài nền tảng, nên không có dòng tiền nào của hệ thống chảy vào tay PT.
 */
public enum WalletOwnerType {

    /** Ví phòng gym — nhận escrow booking, giải ngân sau hoa hồng (UC-057..059). */
    GYM,

    /** Ví khách hàng — nhận tiền hoàn từ refund/tranh chấp (REFUND_CREDIT). */
    CUSTOMER
}
