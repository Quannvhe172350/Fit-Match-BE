package com.fitmatch.service;

import com.fitmatch.service.support.GeoUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    // Hồ Hoàn Kiếm và Lăng Bác — hai mốc Hà Nội cách nhau ~3.1km đường chim bay.
    private static final double HOAN_KIEM_LAT = 21.0287;
    private static final double HOAN_KIEM_LNG = 105.8524;
    private static final double LANG_BAC_LAT = 21.0369;
    private static final double LANG_BAC_LNG = 105.8350;

    @Test
    void haversine_knownHanoiLandmarks() {
        double km = GeoUtils.haversineKm(HOAN_KIEM_LAT, HOAN_KIEM_LNG, LANG_BAC_LAT, LANG_BAC_LNG);
        assertThat(km).isBetween(1.8, 2.4);
    }

    @Test
    void haversine_samePointIsZero() {
        assertThat(GeoUtils.haversineKm(HOAN_KIEM_LAT, HOAN_KIEM_LNG, HOAN_KIEM_LAT, HOAN_KIEM_LNG))
                .isEqualTo(0.0);
    }

    /**
     * Bounding box PHẢI bao trọn hình tròn, nếu không truy vấn sẽ loại nhầm gym
     * nằm trong bán kính trước khi haversine kịp xét tới nó.
     */
    @Test
    void boundingBox_enclosesCircleAtSearchRadius() {
        double radiusKm = 5;
        double latDelta = GeoUtils.latDelta(radiusKm);
        double lngDelta = GeoUtils.lngDelta(HOAN_KIEM_LAT, radiusKm);

        // Điểm cực bắc và cực đông của hình tròn phải nằm trong hộp.
        assertThat(GeoUtils.haversineKm(HOAN_KIEM_LAT, HOAN_KIEM_LNG,
                HOAN_KIEM_LAT + latDelta, HOAN_KIEM_LNG)).isGreaterThanOrEqualTo(radiusKm);
        assertThat(GeoUtils.haversineKm(HOAN_KIEM_LAT, HOAN_KIEM_LNG,
                HOAN_KIEM_LAT, HOAN_KIEM_LNG + lngDelta)).isGreaterThanOrEqualTo(radiusKm);
    }

    @Test
    void lngDelta_nearPoleDoesNotBlowUp() {
        assertThat(GeoUtils.lngDelta(90, 5)).isEqualTo(180.0);
    }

    @Test
    void validation_rejectsOutOfRangeAndNull() {
        assertThat(GeoUtils.isValidLatitude(null)).isFalse();
        assertThat(GeoUtils.isValidLatitude(new BigDecimal("91"))).isFalse();
        assertThat(GeoUtils.isValidLatitude(new BigDecimal("21.0287"))).isTrue();
        assertThat(GeoUtils.isValidLongitude(new BigDecimal("-181"))).isFalse();
        assertThat(GeoUtils.isValidLongitude(new BigDecimal("105.8524"))).isTrue();
    }
}
