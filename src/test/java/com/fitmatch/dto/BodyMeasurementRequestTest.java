package com.fitmatch.dto;

import com.fitmatch.dto.measurement.BodyMeasurementRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** BUG-02: chặn tạo bản ghi số đo không có chỉ số nào. */
class BodyMeasurementRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private BodyMeasurementRequest.BodyMeasurementRequestBuilder base() {
        return BodyMeasurementRequest.builder().measuredAt(LocalDate.now());
    }

    @Test
    void rejectsRequestWithNoMetricAtAll() {
        var violations = VALIDATOR.validate(base().build());

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("atLeastOneMetricPresent");
    }

    /** Chỉ có ghi chú thì vẫn là bản ghi rỗng — không đo được gì. */
    @Test
    void noteAloneIsNotEnough() {
        var violations = VALIDATOR.validate(base().note("thấy khoẻ hơn").build());

        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsRequestWithASingleMetric() {
        var violations = VALIDATOR.validate(base().weightKg(new BigDecimal("72.5")).build());

        assertThat(violations).isEmpty();
    }

    /** Chỉ số nào cũng được tính, không riêng cân nặng. */
    @Test
    void acceptsAnyOneOfTheMetrics() {
        assertThat(VALIDATOR.validate(base().heightCm(new BigDecimal("175")).build())).isEmpty();
        assertThat(VALIDATOR.validate(base().bodyFatPercent(new BigDecimal("18")).build())).isEmpty();
        assertThat(VALIDATOR.validate(base().chestCm(new BigDecimal("98")).build())).isEmpty();
        assertThat(VALIDATOR.validate(base().waistCm(new BigDecimal("82")).build())).isEmpty();
        assertThat(VALIDATOR.validate(base().hipCm(new BigDecimal("95")).build())).isEmpty();
    }
}
