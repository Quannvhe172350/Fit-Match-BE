package com.fitmatch.service;

import java.math.BigDecimal;

/**
 * UC-060 (D-4, audit 2026-07-17): trước đây WalletService.freeze/unfreeze tồn tại
 * nhưng không controller nào expose — admin không thể đóng băng ví gym rủi ro.
 */
public interface AdminWalletService {

    /** Đóng băng một phần available -> frozen của ví gym, kèm lý do (ghi audit). */
    void freeze(Long gymProfileId, BigDecimal amount, String reason, String actorUsername);

    /** Gỡ đóng băng frozen -> available, kèm lý do (ghi audit). */
    void unfreeze(Long gymProfileId, BigDecimal amount, String reason, String actorUsername);
}
