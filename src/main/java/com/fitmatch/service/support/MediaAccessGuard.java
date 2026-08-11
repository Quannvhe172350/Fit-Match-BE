package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.enums.Role;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.ReviewRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nơi duy nhất trả lời câu hỏi "user này được phép gắn/xoá ảnh của entity kia không".
 *
 * <p>Không có bước này thì mọi endpoint media đều là lỗ IDOR: chỉ cần đoán id là
 * đổi được ảnh bìa của gym người khác. Mỗi loại entity có một chủ sở hữu rõ ràng
 * và câu truy vấn kiểm tra luôn ràng buộc theo username đang đăng nhập, chứ không
 * load entity rồi so sánh sau.
 */
@Component
@RequiredArgsConstructor
public class MediaAccessGuard {

    /** Ảnh của các entity này là một phần hồ sơ công khai — ai cũng đọc được. */
    private static final Set<MediaEntityType> PUBLICLY_READABLE = EnumSet.of(
            MediaEntityType.GYM, MediaEntityType.BRANCH, MediaEntityType.SERVICE,
            MediaEntityType.PACKAGE, MediaEntityType.TRAINER, MediaEntityType.REVIEW,
            MediaEntityType.USER);

    private final UserRepository userRepository;
    private final GymProfileRepository gymProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;
    private final PtProfileRepository ptProfileRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /** Admin/Moderator được sửa ảnh của mọi entity (gỡ ảnh vi phạm, sửa hộ đối tác). */
    public boolean isPrivileged(User user) {
        return user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_MODERATOR;
    }

    /**
     * Ném 403/404 nếu {@code user} không được quản lý ảnh của entity chỉ định.
     * {@code entityId} null = ảnh nháp, luôn hợp lệ (chủ sở hữu là chính người upload).
     */
    public void requireCanManage(User user, MediaEntityType entityType, Long entityId) {
        if (entityId == null) return;
        if (isPrivileged(user)) return;

        String username = user.getUsername();
        boolean allowed = switch (entityType) {
            case USER -> entityId.equals(user.getId());
            case GYM -> gymProfileRepository.findByUser_Username(username)
                    .map(g -> g.getId().equals(entityId)).orElse(false);
            case BRANCH -> gymBranchRepository
                    .findByIdAndGymProfile_User_Username(entityId, username).isPresent();
            case SERVICE -> gymServiceRepository
                    .findByIdAndGymProfile_User_Username(entityId, username).isPresent();
            case PACKAGE -> trainingPackageRepository
                    .findByIdAndGymProfile_User_Username(entityId, username).isPresent();
            // PT tự sửa ảnh hồ sơ mình; Gym chủ quản cũng sửa được vì PT do Gym tạo (UC-019).
            case TRAINER -> ptProfileRepository.findByUser_Username(username)
                    .map(p -> p.getId().equals(entityId)).orElse(false)
                    || ptProfileRepository.findByIdAndGymProfile_User_Username(entityId, username).isPresent();
            // Ảnh check-in là bằng chứng buổi tập của chính khách (UC-046).
            case CHECK_IN -> bookingRepository.findByIdAndCustomer_Username(entityId, username).isPresent();
            case REVIEW -> reviewRepository.findByIdAndCustomer_Username(entityId, username).isPresent();
        };

        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "You are not allowed to manage images of this " + entityType);
        }
    }

    /**
     * Ai được XEM danh sách ảnh. Ảnh hồ sơ công khai thì mở cho cả khách vãng lai;
     * ảnh check-in chỉ chủ booking (hoặc gym/admin) mới xem được.
     */
    public void requireCanRead(String username, MediaEntityType entityType, Long entityId) {
        if (PUBLICLY_READABLE.contains(entityType)) return;
        if (username == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required");
        }
        User user = requireUser(username);
        if (isPrivileged(user)) return;
        if (entityType == MediaEntityType.CHECK_IN && entityId != null) {
            boolean isCustomer = bookingRepository.findByIdAndCustomer_Username(entityId, username).isPresent();
            boolean isGym = bookingRepository.findById(entityId)
                    .map(b -> b.getGymProfile().getUser().getUsername().equals(username))
                    .orElse(false);
            if (isCustomer || isGym) return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to view these images");
    }

    /**
     * Cặp entityType/imageType có hợp lệ không — chặn ví dụ "REVIEW + COVER" tạo ra
     * dữ liệu vô nghĩa mà không màn hình nào đọc.
     */
    public void requireValidCombination(MediaEntityType entityType, MediaImageType imageType) {
        boolean valid = switch (entityType) {
            case USER, TRAINER -> imageType == MediaImageType.AVATAR
                    || imageType == MediaImageType.COVER
                    || imageType == MediaImageType.GALLERY;
            case GYM, BRANCH -> imageType == MediaImageType.COVER
                    || imageType == MediaImageType.GALLERY;
            case SERVICE, PACKAGE -> imageType == MediaImageType.GALLERY;
            case REVIEW -> imageType == MediaImageType.REVIEW_IMAGE;
            case CHECK_IN -> imageType == MediaImageType.CHECKIN_IMAGE;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    imageType + " is not a valid image type for " + entityType);
        }
    }
}
