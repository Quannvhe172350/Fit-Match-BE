package com.fitmatch.service.support;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Wallet;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tìm (và tạo nếu chưa có) ví của người đang đăng nhập theo vai trò họ đang thao
 * tác (V61). Ví được tạo lazy ở lần truy cập đầu — không cần backfill ví cho toàn
 * bộ user hiện có, và user chưa từng có dòng tiền nào cũng không sinh bản ghi rác.
 * <p>
 * Ví Gym vẫn đi qua {@link GymProfileResolver} để giữ nguyên ràng buộc "gym phải
 * APPROVED" của UC-061/062. Khách hàng không có cổng trạng thái tương ứng: tiền
 * hoàn đã vào ví là tiền của họ, muốn chặn thì Admin đóng băng ví (UC-060).
 */
@Component
@RequiredArgsConstructor
public class WalletOwnerResolver {

    private final GymProfileResolver gymProfileResolver;
    private final UserRepository userRepository;
    private final WalletService walletService;

    /** Ví tương ứng vai trò {@code ownerType} của user; tạo mới nếu chưa có. */
    @Transactional
    public Wallet resolve(String username, WalletOwnerType ownerType) {
        return switch (ownerType) {
            case GYM -> walletService.getOrCreate(gymProfileResolver.requireApprovedGym(username));
            case CUSTOMER -> walletService.getOrCreateForCustomer(requireUser(username));
        };
    }

    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
}
