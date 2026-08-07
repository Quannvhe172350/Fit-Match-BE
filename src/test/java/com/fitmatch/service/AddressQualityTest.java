package com.fitmatch.service;

import com.fitmatch.entity.GymProfile;
import com.fitmatch.service.support.AddressQuality;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** V59 (UC-18): tự bật cờ "cần xác minh lại địa chỉ" theo độ chính xác geocode. */
class AddressQualityTest {

    private static GymProfile verifiedProfile() {
        return GymProfile.builder().addressVerified(true).build();
    }

    @Test
    void approximateResult_flagsProfileWithActionableNote() {
        GymProfile profile = verifiedProfile();

        assertThat(AddressQuality.flagIfImprecise(profile, "APPROXIMATE")).isTrue();
        assertThat(profile.isAddressVerified()).isFalse();
        assertThat(profile.getAddressReviewNote()).isEqualTo(AddressQuality.IMPRECISE_NOTE);
    }

    @Test
    void preciseResult_leavesProfileAlone() {
        GymProfile profile = verifiedProfile();

        assertThat(AddressQuality.flagIfImprecise(profile, "ROOFTOP")).isFalse();
        assertThat(profile.isAddressVerified()).isTrue();
        assertThat(profile.getAddressReviewNote()).isNull();
    }

    /** Ghi đè sẽ thay lý do cụ thể của Admin bằng một câu tự sinh chung chung. */
    @Test
    void alreadyFlagged_keepsAdminNote() {
        GymProfile profile = GymProfile.builder()
                .addressVerified(false)
                .addressReviewNote("Địa chỉ này là bãi đất trống, vui lòng nộp ảnh mặt tiền.")
                .build();

        assertThat(AddressQuality.flagIfImprecise(profile, "APPROXIMATE")).isFalse();
        assertThat(profile.getAddressReviewNote())
                .isEqualTo("Địa chỉ này là bãi đất trống, vui lòng nộp ảnh mặt tiền.");
    }

    /** Kết quả đẹp KHÔNG tự gỡ cờ — Admin có thể đã gắn vì lý do khác hẳn. */
    @Test
    void preciseResult_doesNotClearAnExistingFlag() {
        GymProfile profile = GymProfile.builder()
                .addressVerified(false)
                .addressReviewNote("Sai tỉnh.")
                .build();

        AddressQuality.flagIfImprecise(profile, "ROOFTOP");

        assertThat(profile.isAddressVerified()).isFalse();
        assertThat(profile.getAddressReviewNote()).isEqualTo("Sai tỉnh.");
    }
}
