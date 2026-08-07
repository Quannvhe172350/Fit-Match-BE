package com.fitmatch.service.support;

import com.fitmatch.entity.GymProfile;

/**
 * Áp cờ "cần xác minh lại địa chỉ" (V58) theo độ chính xác geocode (V59, UC-18).
 *
 * <p>Tách riêng vì có HAI đường ghi toạ độ vào hồ sơ gym — operator tự lưu và
 * job backfill — mà cả hai phải gắn cờ giống hệt nhau, kể cả câu ghi chú gửi cho
 * gym đọc.
 */
public final class AddressQuality {

    /**
     * Ghi chú gửi cho chủ gym. Viết theo hướng "làm gì tiếp theo" chứ không chỉ
     * báo lỗi: gym đọc xong phải biết cách sửa mà không cần hỏi support.
     */
    public static final String IMPRECISE_NOTE =
            "Địa chỉ chỉ khớp được tới mức tương đối (phường/quận), chưa xác định được số nhà. "
                    + "Vui lòng chọn lại địa chỉ từ gợi ý bản đồ.";

    private AddressQuality() {
    }

    /**
     * Bật cờ khi kết quả geocode quá mơ hồ.
     *
     * <p>Không đụng vào hồ sơ ĐANG bị gắn cờ: ghi đè sẽ xoá mất lý do cụ thể Admin
     * đã viết bằng một câu tự sinh chung chung. Cũng không tự GỠ cờ khi kết quả
     * đẹp — Admin có thể đã gắn cờ vì lý do khác hẳn (địa chỉ ma, sai tỉnh), gỡ cờ
     * là việc của luồng "operator sửa lại địa chỉ".
     *
     * @return true nếu vừa bật cờ
     */
    public static boolean flagIfImprecise(GymProfile profile, String locationType) {
        if (!profile.isAddressVerified() || !AddressGeocoder.isImprecise(locationType)) {
            return false;
        }
        profile.setAddressVerified(false);
        profile.setAddressReviewNote(IMPRECISE_NOTE);
        return true;
    }
}
