package com.fitmatch.service;

import com.fitmatch.service.support.AddressGeocoder;
import com.fitmatch.service.support.LocationPrecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V65 (UC-18): quy đổi độ chính xác của từng nhà cung cấp về một từ vựng chung.
 *
 * <p>Đây là chốt chặn cho một lỗi KHÔNG ồn ào: nếu một provider quên quy đổi,
 * {@code locationType} về null, {@link AddressGeocoder#isImprecise} luôn trả
 * false, và cơ chế tự bắt gym xác minh lại địa chỉ ngừng hoạt động hoàn toàn —
 * không ngoại lệ, không log, chỉ là không còn hồ sơ nào bị gắn cờ.
 */
class LocationPrecisionTest {

    @Test
    void googleVocabulary_mapsOneToOne() {
        assertThat(LocationPrecision.fromGoogle("ROOFTOP")).isEqualTo(LocationPrecision.ROOFTOP);
        assertThat(LocationPrecision.fromGoogle("RANGE_INTERPOLATED"))
                .isEqualTo(LocationPrecision.RANGE_INTERPOLATED);
        assertThat(LocationPrecision.fromGoogle("GEOMETRIC_CENTER"))
                .isEqualTo(LocationPrecision.GEOMETRIC_CENTER);
        assertThat(LocationPrecision.fromGoogle("APPROXIMATE")).isEqualTo(LocationPrecision.APPROXIMATE);
    }

    /** Chuỗi lạ -> "không rõ", KHÔNG được đoán thành APPROXIMATE và bắt gym xác minh oan. */
    @Test
    void unknownVocabulary_yieldsNullRatherThanGuessing() {
        assertThat(LocationPrecision.fromGoogle("SOMETHING_NEW")).isNull();
        assertThat(LocationPrecision.fromGoogle(null)).isNull();
        assertThat(LocationPrecision.fromNominatim("chưa-gặp-bao-giờ")).isNull();
    }

    @Test
    void geoapifyResultType_decidesPrecision() {
        assertThat(LocationPrecision.fromGeoapify("building", 0.95)).isEqualTo(LocationPrecision.ROOFTOP);
        assertThat(LocationPrecision.fromGeoapify("street", 0.8))
                .isEqualTo(LocationPrecision.GEOMETRIC_CENTER);
        assertThat(LocationPrecision.fromGeoapify("city", 0.99)).isEqualTo(LocationPrecision.APPROXIMATE);
    }

    /** Geoapify vẫn gán result_type=building cho những khớp rất lỏng — confidence phải hạ cấp được. */
    @Test
    void lowConfidenceBuilding_isDowngraded() {
        assertThat(LocationPrecision.fromGeoapify("building", 0.2))
                .isEqualTo(LocationPrecision.GEOMETRIC_CENTER);
    }

    @Test
    void geoapifyWithoutResultType_fallsBackToConfidence() {
        assertThat(LocationPrecision.fromGeoapify(null, 0.95)).isEqualTo(LocationPrecision.ROOFTOP);
        assertThat(LocationPrecision.fromGeoapify(null, 0.2)).isEqualTo(LocationPrecision.APPROXIMATE);
        assertThat(LocationPrecision.fromGeoapify(null, null)).isNull();
    }

    @Test
    void nominatimAddressType_decidesPrecision() {
        assertThat(LocationPrecision.fromNominatim("building")).isEqualTo(LocationPrecision.ROOFTOP);
        assertThat(LocationPrecision.fromNominatim("road")).isEqualTo(LocationPrecision.GEOMETRIC_CENTER);
        assertThat(LocationPrecision.fromNominatim("city")).isEqualTo(LocationPrecision.APPROXIMATE);
    }

    /**
     * Điểm nối quan trọng nhất: giá trị đem đi lưu phải là thứ mà
     * {@code isImprecise} nhận ra — với MỌI provider, không riêng Google.
     */
    @Test
    void approximateFromEveryProvider_triggersReverification() {
        assertThat(AddressGeocoder.isImprecise(
                LocationPrecision.fromGoogle("APPROXIMATE").storedValue())).isTrue();
        assertThat(AddressGeocoder.isImprecise(
                LocationPrecision.fromGeoapify("city", 0.9).storedValue())).isTrue();
        assertThat(AddressGeocoder.isImprecise(
                LocationPrecision.fromNominatim("suburb").storedValue())).isTrue();

        assertThat(AddressGeocoder.isImprecise(
                LocationPrecision.fromGeoapify("building", 0.95).storedValue())).isFalse();
        // Ghim tay không có độ chính xác nào để chấm — không bao giờ bị coi là kém.
        assertThat(AddressGeocoder.isImprecise(null)).isFalse();
    }

    /** Giá trị lưu phải TRÙNG chuỗi đang nằm sẵn trong cột location_type của dữ liệu cũ. */
    @Test
    void storedValue_matchesLegacyRows() {
        assertThat(LocationPrecision.ROOFTOP.storedValue()).isEqualTo("ROOFTOP");
        assertThat(LocationPrecision.APPROXIMATE.storedValue()).isEqualTo("APPROXIMATE");
    }
}
