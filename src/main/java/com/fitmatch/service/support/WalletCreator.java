package com.fitmatch.service.support;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Wallet;
import com.fitmatch.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo ví trong transaction RIÊNG (REQUIRES_NEW) để cuộc đua tạo ví lần đầu không
 * kéo sập transaction của lời gọi.
 * <p>
 * Ví được tạo lazy ở lần truy cập đầu, mà FE lại bắn nhiều request song song khi
 * mở màn hình ví (số dư + sổ cái + danh sách lệnh rút). Cả mấy request cùng thấy
 * ví chưa tồn tại rồi cùng INSERT, và {@code UK_wallets_user} /
 * {@code gym_profile_id UNIQUE} chặn những request thua cuộc.
 * <p>
 * Nếu INSERT chạy thẳng trong transaction của request thì lỗi UNIQUE đánh dấu
 * transaction đó rollback-only — bắt exception rồi đọc lại cũng vô ích vì mọi
 * truy vấn sau đều hỏng. Tách sang transaction riêng thì chỉ INSERT hỏng bị bỏ,
 * transaction gọi vẫn sạch để đọc lại ví mà request thắng cuộc đã tạo.
 * <p>
 * {@code saveAndFlush} để lỗi UNIQUE nổ ngay tại đây thay vì lúc commit, đúng
 * chỗ mà bên gọi đang bắt.
 */
@Component
@RequiredArgsConstructor
public class WalletCreator {

    private final WalletRepository walletRepository;

    /**
     * {@code gymProfile} thuộc persistence context của bên gọi nên ở đây là
     * detached; association không cascade nên Hibernate chỉ ghi khoá ngoại.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createForGym(GymProfile gymProfile) {
        walletRepository.saveAndFlush(Wallet.builder()
                .ownerType(WalletOwnerType.GYM)
                .gymProfile(gymProfile)
                .build());
    }

    /** V61 — ví khách hàng; xem ghi chú detached ở {@link #createForGym}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createForCustomer(User user) {
        walletRepository.saveAndFlush(Wallet.builder()
                .ownerType(WalletOwnerType.CUSTOMER)
                .user(user)
                .build());
    }
}
